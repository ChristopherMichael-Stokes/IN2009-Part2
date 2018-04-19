package interp;

import java.util.LinkedList;
import java.util.List;
import staticanalysis.ClassSignature;
import staticanalysis.MethodSignature;
import visitor.VisitorAdapter;
import staticanalysis.SymbolTable;
import syntaxtree.*;
import syntaxtree.interp.*;

/**
 * Interpreter visitors.
 *
 * Expressions of all types evaluate to an Integer, with the following meanings
 * for different types:
 *
 * int: the value of the expression
 *
 * boolean: 0 for false, 1 for true
 *
 * class and array types: the "memory address" for an object in the heap (in
 * fact, the key under which the object is stored in the map representing the
 * heap).
 *
 * Statements all evaluate to null, with side-effects on the Moopl run-time.
 *
 * @see MooplRunTime
 */
public class Interpreter extends VisitorAdapter<Integer> {

    private static final int FALSE = 0, TRUE = 1;

    /**
     * The symbol table.
     */
    private SymbolTable symTab;

    /**
     * The current run-time state.
     */
    private MooplRunTime mooplRunTime;

    /**
     * Initialise a new interpreter.
     *
     * @param symTab the symbol table which contains the class and method
     * signatures
     */
    public Interpreter(SymbolTable symTab) {
        this.symTab = symTab;
        mooplRunTime = new MooplRunTime();
    }

    /*
     The FunDecl and ProcDecl visit methods allow us to process a method call
     by setting up the call stack and then simply visiting the method declaration.
     @see visit(ICall)
     @see visit(StmCall)
     @see visit(ExpCall)
     @see visit(ExpNewObject)
     */
    // Type t;
    // String id;
    // List<Formal> fs;
    // List<Stm> ss;
    // Exp e;
    public Integer visit(FunDecl n) {
        for (Stm s : n.ss) {
            s.accept(this);
        }
        return n.e.accept(this);
    }

    // String id;
    // List<Formal> fs;
    // List<Stm> ss;
    public Integer visit(ProcDecl n) {
        for (Stm s : n.ss) {
            s.accept(this);
        }
        return null;
    }

    /*=============================================*/
 /* Expression visitors (all return an Integer) */
 /*=============================================*/
    public Integer visit(ExpInteger n) {        
        return n.i;
    }

    public Integer visit(ExpArrayLength n) {    //TODO
        return -1;
    }

    public Integer visit(ExpArrayLookup n) {    //TODO

        return -1;
    }

    public Integer visit(ExpCall n) {    //TODO

        return -1;
    }

    public Integer visit(ExpTrue n) {
        return TRUE;
    }
    
    public Integer visit(ExpFalse n) {
        return FALSE;
    }

    public Integer visit(ExpIsnull n) {    //TODO
        

        return -1;
    }

    public Integer visit(ExpNewArray n) {    //TODO
        
        return -1;
    }

    public Integer visit(ExpNewObject n) {    //TODO

        return -1;
    }

    public Integer visit(ExpNot n) {    //TODO
        int val = n.accept(this);
        return val == 0 ? 1 : 0;
    }

    public Integer visit(ExpOp n) {    //TODO
        ExpOp.Op op = n.op;
        Integer exp1 = n.e1.accept(this), exp2 = n.e2.accept(this);
        switch(op) {
            case AND:
                return (exp1 & exp2) == exp1 ? TRUE : FALSE;
            case DIV:
                if (exp2 == 0) 
                    return Integer.MIN_VALUE;

                return exp1 / exp2;
            case EQUALS:
                return exp1 == exp2 ? TRUE : FALSE;
            case LESSTHAN:
                return exp1 < exp2 ? TRUE : FALSE;
            case MINUS:
                return exp1 - exp2;
            case PLUS:
                return exp1 + exp2;
            case TIMES:
                return exp1 * exp2;
            default:
                return -1;
        }
    }

    public Integer visit(ExpSelf n) {    //TODO
        return mooplRunTime.getFrameEntry(-2);
    }

   

    public Integer visit(ExpVar n) {    //TODO
        
        return -1;
    }

    /*======================================*/
 /* Statement visitors (all return null) */
 /*======================================*/
    public Integer visit(StmArrayAssign n) {    //TODO
        return null;
    }

    public Integer visit(StmAssign n) {    //TODO

        return null;
    }

    public Integer visit(StmBlock n) {    //TODO

        return null;
    }

    public Integer visit(StmCall n) {    //TODO

        return null;
    }

    public Integer visit(StmIf n) {    //TODO

        return null;
    }

    public Integer visit(StmOutput n) {
        int v = n.e.accept(this);
        System.out.println(v);
        return null;
    }

    /*=====================================*/
 /* ICommand visitors (all return null) */
 /*=====================================*/
    // String id
    // List<Exp> es
    public Integer visit(ICall n) {
        // n.id is the name of a top-level procedure
        // n.es is the list of actual-parameter expressions for this call

        // evaluate the actual-parameter expressions
        List<Integer> actualValues = new LinkedList<Integer>();
        for (Exp e : n.es) {
            actualValues.add(e.accept(this));
        }

        // look up the top-level procedure signature in the dummy top-level
        // class (class name null)
        ClassSignature dummyClassSig = symTab.getClassSignature(null);
        MethodSignature procSig = dummyClassSig.getMethodSignature(n.id);

        // find the "code" (MethodDecl) for the procedure
        MethodDecl procCode = procSig.getMethodDecl();

        // push a new stack frame on the call-stack
        // there is no "self" for a top-level proc, so we pass 0 as an
        // appropriate dummy value (it will never be used)
        mooplRunTime.pushFrame(0, actualValues, procCode.stackAllocation);

        // execute the procedure code
        // @see visit(ProcDecl)
        procCode.accept(this);

        // method call completed, so pop the frame off the call-stack
        mooplRunTime.popFrame();

        // procedures don't return a value, so we return null
        return null;
    }

    // Exp e
    public Integer visit(IEval n) {
        System.out.println(n.e.accept(this));
        return null;
    }
}
