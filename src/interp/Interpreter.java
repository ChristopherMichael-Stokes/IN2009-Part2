package interp;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
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

    /**
     * constants
     */
    private static final int FALSE = 0, TRUE = 1, SELF = -2, NULL = 0;

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

    public Integer visit(ExpArrayLength n) {
        return mooplRunTime.deref(n.e.accept(this)).elements.length;
    }

    public Integer visit(ExpArrayLookup n) {
        MooplObject array = mooplRunTime.deref(n.e1.accept(this));
        return array.elements[n.e2.accept(this)];
    }

    public Integer visit(ExpCall n) {
        return call(n.e, n.id, n.es, n.getClass());
    }

    public Integer visit(ExpTrue n) {
        return TRUE;
    }

    public Integer visit(ExpFalse n) {
        return FALSE;
    }

    public Integer visit(ExpIsnull n) {
        return n.e.accept(this) == NULL ? TRUE : FALSE;
    }

    public Integer visit(ExpNewArray n) {
        Integer length = n.e.accept(this);
        return mooplRunTime.allocArrayObject(length, n.t);
    }

    public Integer visit(ExpNewObject n) {
        String currentClassName = n.id;
        ClassSignature classSig;
        int fieldCount = 0;
        //get count of total fields + inherited fields
        do {
            classSig = symTab.getClassSignature(currentClassName);
            fieldCount += classSig.getImmediateFieldCount();
        } while ((currentClassName = classSig.getParentName()) != null);
        
        Integer address = mooplRunTime.allocClassInstance(fieldCount, n.id);
        MooplObject mo = mooplRunTime.deref(address);
        String classType = mo.type.toString();

        //call constructor 
        //constructor is a proc, so pass a StmCall
        call(address, mo.type.toString(), n.es, StmCall.class);
        System.err.print(n.id+"[ ");
        for (int i : mo.elements) {
            System.err.print(i+" ");
        }
        System.err.print(" ]\n");
        
        return address;
    }

    public Integer visit(ExpNot n) {
        int val = n.e.accept(this);
        return val == FALSE ? TRUE : FALSE;
    }

    public Integer visit(ExpOp n) {
        int exp1 = n.e1.accept(this), exp2 = n.e2.accept(this);
        switch (n.op) {
            case AND:
                //binary and should work, no matter the values of true or false
                //otherwise (exp1 & exp2) == TRUE ? TRUE : FALSE; would work
                return exp1 & exp2;
            case DIV:
                if (exp2 == 0) {
                    throw new MooplRunTimeException("division by zero is undefined");
                }
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
                throw new MooplRunTimeException("handling for op "+n.op.name()
                        +" has not been implemented in this compiler version");
        }
    }

    public Integer visit(ExpSelf n) {
        return mooplRunTime.getFrameEntry(SELF);
    }

    public Integer visit(ExpVar n) {
        if (n.v.isStackAllocated) {
            return mooplRunTime.getFrameEntry(n.v.offset);
        } else {
            MooplObject mo = mooplRunTime.deref(mooplRunTime.getFrameEntry(SELF));
            return mo.elements[n.v.offset];
        }
    }

    /*======================================*/
 /* Statement visitors (all return null) */
 /*======================================*/
    public Integer visit(StmOutput n) {
        int v = n.e.accept(this);
        System.out.println(v);
        return null;
    }

    public Integer visit(StmArrayAssign n) {
        int idx = n.e2.accept(this);
        int val = n.e3.accept(this);

        MooplObject array = mooplRunTime.deref(n.e1.accept(this));
        array.elements[idx] = val;
        return null;
    }

    public Integer visit(StmAssign n) {
        int value = n.e.accept(this);
        if (n.v.isStackAllocated) {
            mooplRunTime.setFrameEntry(n.v.offset, value);
        } else {
            int address = mooplRunTime.getFrameEntry(SELF);
            MooplObject mo = mooplRunTime.deref(address);
            mo.elements[n.v.offset] = value;
        }
        return null;
    }

    public Integer visit(StmBlock n) {
        for (Stm s : n.ss) {
            s.accept(this);
        }
        return null;
    }

    public Integer visit(StmCall n) {
        call(n.e, n.id, n.es, n.getClass());
        return null;
    }

    public Integer visit(StmIf n) {
        if (n.e.accept(this) == TRUE) {
            n.b1.accept(this);
        } else {
            n.b2.accept(this);
        }
        return null;
    }

    public Integer visit(StmVarDecl n) { //TODO
        if (n.t instanceof TypeArray) {

        } else if (n.t instanceof TypeBoolean) {

        } else if (n.t instanceof TypeInt) {

        } else if (n.t instanceof TypeClassType) {

        }
        return null;
    }

    public Integer visit(StmWhile n) { //TODO      
        List<Stm> statements = n.b.ss;
        while (n.e.accept(this) == TRUE) {
            n.b.accept(this);
        }
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

    //convenience method to reduce code
    private Integer call(Exp e, String id, List<Exp> es, Class callType) {
        int address = e.accept(this);
        if (address == NULL) {
            throw new MooplRunTimeException("null pointer exception, attempting"
                    + " to call method \""+id+"\" on an uninitalized object "
                    + e.getTags());
        }
        return call(address, id, es, callType);
    }
    
    private Integer call(int address, String id, List<Exp> es, Class callType) {
        MooplObject mo = mooplRunTime.deref(address);
        
//        //stream of list should always be sequential, possibly redundant op
//        List<Integer> params = es.stream().sequential()
//                .map(e_ -> e_.accept(this))
//                .collect(Collectors.toList());

        List<Integer> params = new LinkedList<>();
        for (Exp e_ : es) {
            params.add(e_.accept(this));
        }
        String classType = mo.type.toString();

        ClassSignature classSignature = symTab.getClassSignature(classType);
        MethodSignature methodSig = classSignature.getMethodSignature(id);

        //attempt to find signature from super type
        while (methodSig == null) {
            String parent = classSignature.getParentName();
            if (parent == null) {
                //type checker should throw an error before this occurs, so pointless to check
                throw new MooplRunTimeException("cannot find method signature in inheritance hierarchy");
            }

            classSignature = symTab.getClassSignature(parent);
            methodSig = classSignature.getMethodSignature(id);
        }
        MethodDecl methodDecl = methodSig.getMethodDecl();

        mooplRunTime.pushFrame(address, params, methodDecl.stackAllocation);
        for (Stm s : methodDecl.ss) {
            s.accept(this);
        }

        //get return value only if input is an exp call
        Integer retVal = null;
        if (callType.equals(ExpCall.class)) {
            retVal = methodDecl.accept(this);
        }
        mooplRunTime.popFrame();

        return retVal;
    }
}
