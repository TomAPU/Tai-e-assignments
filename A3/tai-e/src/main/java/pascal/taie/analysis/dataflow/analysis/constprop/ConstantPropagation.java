/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.dataflow.analysis.constprop;

import pascal.taie.analysis.dataflow.analysis.AbstractDataflowAnalysis;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.*;
import pascal.taie.ir.stmt.DefinitionStmt;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.language.type.Type;
import pascal.taie.util.AnalysisException;

public class ConstantPropagation extends
        AbstractDataflowAnalysis<Stmt, CPFact> {

    public static final String ID = "constprop";

    public ConstantPropagation(AnalysisConfig config) {
        super(config);
    }

    @Override
    public boolean isForward() {
        return true;
    }

    @Override
    public CPFact newBoundaryFact(CFG<Stmt> cfg) {
        // TODO - finish me
        CPFact fact = new CPFact();
        cfg.getMethod().getIR().getParams().forEach(param->{
            if(canHoldInt(param))
                fact.update(param,Value.getNAC());
        });
        return fact;
    }

    @Override
    public CPFact newInitialFact() {
        // TODO - finish me
        return new CPFact();
    }

    @Override
    public void meetInto(CPFact fact, CPFact target) {
        // TODO - finish me
        fact.forEach((k,v)->{
            //System.out.println("key:"+k+" value:"+v);
            target.update(k,meetValue(v,target.get(k)));
        });
    }

    /**
     * Meets two Values.
     */
    public Value meetValue(Value v1, Value v2) {
        // TODO - finish me
        if(v1.isNAC()||v2.isNAC()) return Value.getNAC();
        if(v1.isUndef()) return v2;
        if(v2.isUndef()) return v1;
        return v1.getConstant()== v2.getConstant()? v1 : Value.getNAC();
    }

    @Override
    public boolean transferNode(Stmt stmt, CPFact in, CPFact out) {
        // TODO - finish me
        //System.out.println("transfernode:"+stmt);
        CPFact newout=new CPFact();
        newout.copyFrom(in);
        if(stmt instanceof  DefinitionStmt){

            var lv=((DefinitionStmt<?, ?>) stmt).getLValue();
            var rv=((DefinitionStmt<?, ?>) stmt).getRValue();
            //if(lv!=null && canHoldInt((Var)lv)){
            if(lv instanceof Var && canHoldInt((Var)lv)){
                newout.update((Var) lv,evaluate(rv,in));
            }
        }
        return out.copyFrom(newout);
    }

    /**
     * @return true if the given variable can hold integer value, otherwise false.
     */
    public static boolean canHoldInt(Var var) {
        Type type = var.getType();
        if (type instanceof PrimitiveType) {
            switch ((PrimitiveType) type) {
                case BYTE:
                case SHORT:
                case INT:
                case CHAR:
                case BOOLEAN:
                    return true;
            }
        }
        return false;
    }

    /**
     * Evaluates the {@link Value} of given expression.
     *
     * @param exp the expression to be evaluated
     * @param in  IN fact of the statement
     * @return the resulting {@link Value}
     */
    public static Value evaluate(Exp exp, CPFact in) {
        // TODO - finish me

        //Var or IntLiteral
        if(exp instanceof Var) return in.get((Var)exp);
        if(exp instanceof IntLiteral)   return Value.makeConstant(((IntLiteral) exp).getValue());
        if(! (exp instanceof BinaryExp)) return Value.getNAC();
        //BinaryExp
        var op1=evaluate(((BinaryExp) exp).getOperand1(),in);
        var op2=evaluate(((BinaryExp) exp).getOperand2(),in);
        var operator=((BinaryExp) exp).getOperator().toString();
        //bug fix
        //THIS LINE IS SO FUCKING IMPORTANT THAT HAS STUCKED ME FOR 5+ DAYS!
        if(op2.isConstant() && op2.getConstant()==0 && (operator=="/"||operator=="%")) return Value.getUndef();
        //bad case!
        if(op1.isNAC()||op2.isNAC()) return Value.getNAC();
        if(op1.isUndef()||op2.isUndef()) return Value.getUndef();
        //convert to constant

        var a=op1.getConstant();
        var b=op2.getConstant();

        switch(operator){
            case "+":
                return Value.makeConstant(a+b);
            case "-":
                return Value.makeConstant(a-b);
            case "*":
                return Value.makeConstant(a*b);
            case "/":
                return b==0?Value.getUndef():Value.makeConstant(a/b);
            case "%":
                return b==0?Value.getUndef():Value.makeConstant(a%b);
            case "==":
                return Value.makeConstant(a==b?1:0);
            case "!=":
                return Value.makeConstant(a!=b?1:0);
            case "<":
                return Value.makeConstant(a<b?1:0);
            case ">":
                return Value.makeConstant(a>b?1:0);
            case "<=":
                return Value.makeConstant(a<=b?1:0);
            case ">=":
                return Value.makeConstant(a>=b?1:0);
            case "<<":
                return Value.makeConstant(a<<b);
            case ">>":
                return Value.makeConstant(a>>b);
            case ">>>":
                return Value.makeConstant(a>>>b);
            case "|":
                return Value.makeConstant(a|b);
            case "&":
                return Value.makeConstant(a&b);
            case "^":
                return Value.makeConstant(a^b);
            default:
                return Value.getUndef();
        }
    }
}
