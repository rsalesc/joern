package astnodes.builders;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;


import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import parsing.InitDeclContextWrapper;
import parsing.ParseTreeUtils;

import astnodes.ASTNodeBuilder;
import astnodes.declarations.IdentifierDecl;
import astnodes.declarations.IdentifierDeclType;
import astnodes.expressions.Identifier;


public class IdentifierDeclBuilder extends ASTNodeBuilder
{
	IdentifierDecl thisItem;
	
	@Override
	public void createNew(ParserRuleContext ctx)
	{
		item = new IdentifierDecl();
		thisItem = (IdentifierDecl) item;
		item.initializeFromContext(ctx);
	}

	public void setType(InitDeclContextWrapper decl_ctx,
						ParserRuleContext typeName)
	{
		String baseType = "";
		if(typeName != null)
			baseType = ParseTreeUtils.childTokenString(typeName);
		String completeType = baseType;
		if(decl_ctx.ptrs() != null)
			completeType += " " + ParseTreeUtils.childTokenString(decl_ctx.ptrs());
		if(decl_ctx.type_suffix() != null)
			completeType += " " + ParseTreeUtils.childTokenString(decl_ctx.type_suffix());
		
		thisItem.type = new IdentifierDeclType();
		thisItem.type.baseType = baseType;
		thisItem.type.completeType = completeType;
	}

	public void setName(InitDeclContextWrapper decl_ctx)
	{
		ParserRuleContext identifier = decl_ctx.identifier();
		thisItem.name = new Identifier();
		thisItem.name.initializeFromContext(identifier);
	}
	
	public List<IdentifierDecl> getDeclarations(ParserRuleContext decl_list,
												ParserRuleContext typeName)
	{
		List<IdentifierDecl> declarations = new LinkedList<IdentifierDecl>();
		InitDeclContextWrapper decl_ctx;
		for(Iterator<ParseTree> i = decl_list.children.iterator(); i.hasNext();)
		{
			
			decl_ctx = new InitDeclContextWrapper(i.next());
			// for ','s
			if(decl_ctx.getWrappedObject() == null) continue;
			
			
			createNew(decl_ctx.getWrappedObject());
			setName(decl_ctx);
			setType(decl_ctx, typeName);
			declarations.add((IdentifierDecl) getItem());
		}
		return declarations;
	}
	
	
}
