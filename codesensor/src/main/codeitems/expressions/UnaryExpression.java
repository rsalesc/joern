package main.codeitems.expressions;

import main.codeitems.CodeItemVisitor;

public class UnaryExpression extends Expression
{
	public void accept(CodeItemVisitor visitor){ visitor.visit(this); }	
}
