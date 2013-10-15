
Object.metaClass.setSinkArgument =  { callRegex, argNum, argRegex = null ->
  x = _().getCallsToRegex(callRegex).callToArgumentN(argNum)
  if(argRegex != null) x = x.filter{ it.code.matches(argRegex) }
  return x
}

Gremlin.defineStep('reaches', [Vertex, Pipe], {
  _().out('REACHES').loop(1){ it.loops < 20}{true}
})

Gremlin.defineStep('reachesUnaltered', [Vertex, Pipe], {
  _().sideEffect{ val = it[1];}.transform{ it[0] }.outE('REACHES')
  .filter{if(!val) return true; else return it.var.equals(val)}.inV()
})

// TODO: eliminate copy/paste code. Optimize.

// For a given AST node (e.g., argument), get all sources connected to it by
// data flow, which match one of the supplied regular expressions.
// Returns list of (sourceId, sinkId) tuples.

Gremlin.defineStep('dataFlowFromRegex', [Vertex, Pipe], { Object [] s ->
  def sources = []
  
  // compile regular expressions
  for (aPattern in s){
    sources.add(Pattern.compile(aPattern))
  }
  
  // get all basic blocks in function, which match regex:
  _()
  .sideEffect{ sourceIds = it.astNodeToFunction().functionToBasicBlocks()
               .filter{ aRegexFound(it, sources) }.id.toList() }
  
  .sideEffect{ argNode = it; }
  .out('USE').sideEffect{ symbol = it.code }
  .transform{ argNode }

  .astNodeToBasicBlock().sideEffect{ sinkId = it.id; }
  
  .sideEffect{ firstRound = true }
  .as('loopStart').inE('REACHES').filter{ !firstRound || it.var == symbol }.outV().sideEffect{firstRound = false}.sideEffect{ isSource = (it.id in sourceIds) }
  .loop('loopStart'){it.loops < 2 && !isSource } { isSource }
  .transform{ [it.id, sinkId] } 
  .dedup()
})


// I'm not sure if this one works well with the cache since we are
// passing a closure.

Gremlin.defineStep('dataFlowFrom', [Vertex, Pipe], { f ->
  def fil = f;
  
  // get all basic blocks in function, which match filter
  _().sideEffect{ 
    sourceIds = it.astNodeToFunction().functionToBasicBlocks()
    .filter( exists(fil(it)) ).id.toList()
  }

  .sideEffect{ argNode = it; }
  .astNodeToBasicBlock().sideEffect{ sinkId = it.id; }
  .out('USE').sideEffect{ symbol = it.code }.transform{ argNode }
  .astNodeToBasicBlock().sideEffect{ firstRound = true }
  .as('loopStart').inE('REACHES').filter{ !firstRound || it.var == symbol }.outV().sideEffect{firstRound = false}
  .sideEffect{ isSource = (it.id in sourceIds) }
  .loop('loopStart'){it.loops < 2 && !isSource }{ isSource }
  .transform{ [it.id, sinkId] }
  .dedup()
})

// Input is an AST-node such as a parameter:
// Expects:
// [astNode, symbol]

Gremlin.defineStep('dataFlowToRegex', [Vertex, Pipe], { Object [] s ->
  def sinks = s;
  
  _()
  .sideEffect{ (astNode, sourceSymbol) = it }.transform{ astNode }
  
  .sideEffect{ 
    sinkIds = it.astNodeToFunction().functionToBasicBlocks()
    .filter{ aRegexFound(it, sinks) }.id.toList()
  }
  
  .astNodeToBasicBlock().sideEffect{ sourceId = it.id; firstRound = true }
  .as('loopStart').ifThenElse{!firstRound}{it.inV()}{it}
  .outE('REACHES').filter{ !firstRound || it.var.equals(sourceSymbol) }
  .sideEffect{firstRound = false}
  .sideEffect{ isSink = (it.inV().id.toList()[0] in sinkIds ) }
  .loop('loopStart'){it.loops < 2 && !isSink}{ isSink }
  .transform{ [sourceId, it.inV().toList()[0].id, it.var] } 
  .dedup()
})

// Expects:
// [astNode, sourceSymbol]

Gremlin.defineStep('dataFlowTo', [Vertex, Pipe], { f ->
  def fil = f;
  
  _().sideEffect{ (astNode, sourceSymbol) = it }.transform{ astNode }
  
  .sideEffect{ 
    sinkIds = it.astNodeToFunction().functionToBasicBlocks()
    .filter{ exists(fil(it)) }.id.toList()
  }
  
  .astNodeToBasicBlock().sideEffect{ sourceId = it.id; firstRound = true }
  .as('loopStart').ifThenElse{!firstRound}{it.inV()}{it}
  .outE('REACHES').filter{ !firstRound || it.var.equals(sourceSymbol) }
  .sideEffect{firstRound = false}
  .sideEffect{ isSink = (it.inV().id.toList()[0] in sinkIds ) }
  .loop('loopStart'){it.loops < 2 && !isSink}{ isSink }
  .transform{ [sourceId, it.inV().toList()[0].id, it.var] } 
  .dedup()
})

// Expects:
// [astNode, sourceSymbol]


Gremlin.defineStep('directDataFlowTo', [Vertex, Pipe], { it ->
  _().sideEffect{ sourceSymbol = it[1] }.transform{ it[0] }
  .astNodeToBasicBlock().outE('REACHES').filter{ it.var.equals(sourceSymbol) }.inV()
})

Gremlin.defineStep('isNotSanitizedByRegex', [Vertex, Pipe], { Object [] san ->
  def sanitizers = san;
  
  _().sideEffect{ sourceId = it[0]; sinkId = it[1] }
  .transform{ g.v(sourceId)}
  
  .sideEffect{
    sanIds = it.astNodeToFunction().functionToBasicBlocks()
    .filter{ aRegexFound(it, sanitizers) }.id.toList()
  }
  
  .as('x').out('FLOWS_TO').simplePath()
  .sideEffect{ isSanitizer = (it.id in sanIds) }
  .loop('x'){ it.loops < 40 && it.object.id != sinkId && !isSanitizer}
  .filter{ it.id == sinkId }
  .dedup().transform{ [sourceId, sinkId] }
})

Gremlin.defineStep('isNotSanitizedBy', [Vertex, Pipe], { f ->
  def fil = f;

  _().sideEffect{ sourceId = it[0]; sinkId = it[1] }
  .transform{ g.v(sourceId)}
  
  .sideEffect{
    sanIds = it.astNodeToFunction().functionToBasicBlocks()
    .filter{ exists(fil(it)) }.id.toList()
  }
  
  .as('x').out('FLOWS_TO').simplePath()
  .sideEffect{ isSanitizer = (it.id in sanIds) }
  .loop('x'){ it.loops < 40 && it.object.id != sinkId && !isSanitizer }
  .filter{ it.id == sinkId }
  .dedup().transform{ [sourceId, sinkId] }

})

// interprocedural data flow

Gremlin.defineStep('ipDataFlowFrom', [Vertex, Pipe], { sx-> 
  def ipSource = sx;

  _().astNodeToBasicBlock().sideEffect{ sinkId = it.id; }.back(2)
  .out('USE').sideEffect{ ipSymbol = it.code }.back(2)
  .astNodeToBasicBlock().sideEffect{ firstRound = true; ipDstNode = it.id; ipNodeStack = []}
  
  .as('loopStart')
  .basicBlockToAST()
  
  .ifThenElse{ it.type == 'Parameter' }
  {
    it.out('IS_AST_PARENT')
    .filter{ it.type == 'Identifier'}.in('IS_ARG').astNodeToBasicBlock()
    .sideEffect{ ipNodeStack.add([ipCurNode, ipDstNode]); ipDstNode = it.id }
  }
  // else
  { 
    it.astNodeToBasicBlock().inE('REACHES').filter{ !firstRound || it.var == ipSymbol }.outV()
  }
  .sideEffect{firstRound = false; ipCurNode = it.id; }
  .loop('loopStart'){it.loops < 10 && !it.object.code.contains(ipSource)}{true}
  .filter{ it.code.contains(ipSource) }
  .sideEffect{ if(ipNodeStack.size() == 0){ ipNodeStack.add([it.id,ipDstNode]) }}
  .transform{ [it.id, sinkId] }
  .dedup()
  .transform{ [it[0], it[1], ipNodeStack] }
})


Gremlin.defineStep('ipIsNotSanitizedBy', [Vertex, Pipe], { san ->
  def sanitizer = san;
  
  _().sideEffect{ nodeStack = it[2]; }
  .filter{
    for(x in nodeStack){ 
      def srcId = x[0];
      def dstId = x[1];      
      l = g.v(0).transform{ [srcId, dstId] }.isNotSanitizedBy(sanitizer).toList()
      if(l.size() == 0)
  	return false;
    }
    return true;
  }
})

Object.metaClass.aRegexFound = { it, sanitizers ->
  for(s in sanitizers){
      if(it.code.find(s))
	return true;
    }
  return false;
}

Object.metaClass.allRegexFound = { it, regex ->
  for(r in regexs){
    if(!it.code.find(r))
      return false;
  }
  return true;
}

Gremlin.defineStep('cfgPathsToExit', [Vertex,Pipe], {
 _().out('FLOWS_TO').loop(1){it.loops < 10 }{true}.simplePath.path()
})

Gremlin.defineStep('cfgPathsFromEntry', [Vertex,Pipe], {
 _().in('FLOWS_TO').loop(1){it.loops < 10 }{true}.simplePath.path()
}) 
