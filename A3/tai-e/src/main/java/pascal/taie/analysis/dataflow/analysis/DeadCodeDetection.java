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

package pascal.taie.analysis.dataflow.analysis;

import pascal.taie.analysis.MethodAnalysis;
import pascal.taie.analysis.dataflow.analysis.constprop.CPFact;
import pascal.taie.analysis.dataflow.analysis.constprop.ConstantPropagation;
import pascal.taie.analysis.dataflow.analysis.constprop.Value;
import pascal.taie.analysis.dataflow.fact.DataflowResult;
import pascal.taie.analysis.dataflow.fact.SetFact;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGBuilder;
import pascal.taie.analysis.graph.cfg.Edge;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.*;
import pascal.taie.ir.stmt.AssignStmt;
import pascal.taie.ir.stmt.If;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.ir.stmt.SwitchStmt;
import java.util.*;
import pascal.taie.util.collection.Pair;

import pascal.taie.analysis.graph.cfg.Edge;

public class DeadCodeDetection extends MethodAnalysis {

    public static final String ID = "deadcode";

    public DeadCodeDetection(AnalysisConfig config) {
        super(config);
    }

    @Override
    public Set<Stmt> analyze(IR ir) {
        // obtain CFG
        CFG<Stmt> cfg = ir.getResult(CFGBuilder.ID);
        // obtain result of constant propagation
        DataflowResult<Stmt, CPFact> constants =
                ir.getResult(ConstantPropagation.ID);
        // obtain result of live variable analysis
        DataflowResult<Stmt, SetFact<Var>> liveVars =
                ir.getResult(LiveVariableAnalysis.ID);
        // keep statements (dead code) sorted in the resulting set
        Set<Stmt> deadCode = new TreeSet<>(Comparator.comparing(Stmt::getIndex));

        // TODO - finish me
        // Your task is to recognize dead code in ir and add it to deadCode
        if(ir.getMethod().toString().contains("deadLoop()")){
            System.out.println("here we go");
        }
        // System.out.println("Entry LINE NUMBER:"+cfg.getEntry().getLineNumber());
        var reachable=new ArrayList<Stmt>();
        var alreadyfucked=new TreeSet<>(Comparator.comparing(Stmt::getIndex));
        reachable.add(cfg.getEntry());
        for(var i=0;i< reachable.size();i++){
            var curstmt=reachable.get(i);

            if(alreadyfucked.contains(curstmt)){
                continue;
            }
            alreadyfucked.add(curstmt);
            //unused assign
            if(curstmt instanceof AssignStmt<?,?> s && s.getLValue() instanceof Var var ){
                var leftvar=((AssignStmt<?,?>)curstmt).getLValue();
                var rightvar=((AssignStmt<?,?>)curstmt).getRValue();

                //deaaaad
                if(!(( liveVars.getOutFact(curstmt).contains((Var)leftvar))
                        || !hasNoSideEffect(rightvar))){
                    deadCode.add(curstmt);
                    //continue;
                }
                reachable.addAll(cfg.getSuccsOf(curstmt));
            }
            //if statement
            else if(curstmt instanceof If ){
                var con=ConstantPropagation.evaluate(((If) curstmt).getCondition(),constants.getResult(curstmt));
                for (Edge<Stmt> stmtEdge : cfg.getOutEdgesOf(curstmt)) {
                    if((!con.isConstant())||
                            (con.getConstant()==1 && stmtEdge.getKind()==Edge.Kind.IF_TRUE)||
                            (con.getConstant()==0 && stmtEdge.getKind()==Edge.Kind.IF_FALSE)){
                        reachable.add(stmtEdge.getTarget());
                    }
                }

            }

            else if(curstmt instanceof SwitchStmt ){
                var con=ConstantPropagation.evaluate(((SwitchStmt) curstmt).getVar(),constants.getInFact(curstmt));
                if(!con.isConstant()){
                    reachable.addAll(cfg.getSuccsOf(curstmt));
                    continue;
                }
                var weneeddefault=true;
                for (Edge<Stmt> stmtEdge : cfg.getOutEdgesOf(curstmt)) {
                    if((stmtEdge.isSwitchCase() &&con.getConstant()==stmtEdge.getCaseValue())){
                        reachable.add(stmtEdge.getTarget());
                        weneeddefault=false;
                    }
                }
                if(weneeddefault)
                    reachable.add(((SwitchStmt) curstmt).getDefaultTarget());

            }
            else {
                reachable.addAll(cfg.getSuccsOf(curstmt));
            }

        }

        /*cfg.getNodes()*/ir.getStmts().forEach(stmt -> {
            if((!reachable.contains(stmt))/*&&stmt!= cfg.getExit()*/)
                deadCode.add(stmt);
            if((!reachable.contains(stmt))&&stmt== cfg.getExit()){
                System.out.println("fuuuuck");
            }
        });

        return deadCode;
    }

    /**
     * @return true if given RValue has no side effect, otherwise false.
     */
    private static boolean hasNoSideEffect(RValue rvalue) {
        // new expression modifies the heap
        if (rvalue instanceof NewExp ||
                // cast may trigger ClassCastException
                rvalue instanceof CastExp ||
                // static field access may trigger class initialization
                // instance field access may trigger NPE
                rvalue instanceof FieldAccess ||
                // array access may trigger NPE
                rvalue instanceof ArrayAccess) {
            return false;
        }
        if (rvalue instanceof ArithmeticExp) {
            ArithmeticExp.Op op = ((ArithmeticExp) rvalue).getOperator();
            // may trigger DivideByZeroException
            return op != ArithmeticExp.Op.DIV && op != ArithmeticExp.Op.REM;
        }
        return true;
    }
}
