/*
 * Javassist, a Java-bytecode translator toolkit.
 * Copyright (C) 2012 Roman Levenstein. All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License.  Alternatively, the contents of this file may be used under
 * the terms of the GNU Lesser General Public License Version 2.1 or later,
 * or the Apache License Version 2.0.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 */

package javassist.expr;

import javassist.*;
import javassist.bytecode.*;
import javassist.compiler.*;
import javassist.util.InstructionFinder.InstructionHandle;
import javassist.bytecode.OpcodeInfo;

/**
 * Expression for accessing a matched instruction sequence.
 * TODO: 
 * Introduce syntax for accessing instructions in replacement string.
 * Introduce syntax for using any bytecode instructions in replacement string
 * Introduce syntax for mixing bytecode instructions and Java code in replacement string
 * Introduce a way to replace only a specific instruction (without affecting other matched instructions)in the replacement string
 * Introduce syntax to access/change operands of (matched) bytecode instructions 
 */
public class InsnSequenceExpr extends Expr {
	
	InstructionHandle[] handles;

    protected InsnSequenceExpr(int pos, CodeIterator i, CtClass declaring,
                          MethodInfo m, InstructionHandle[] match) {
        super(pos, i, declaring, m);
        handles = match;
    }

    /**
     * Returns the method or constructor containing the field-access
     * expression represented by this object.
     */
    public CtBehavior where() { return super.where(); }

    /**
     * Returns the line number of the source line containing the
     * field access.
     *
     * @return -1       if this information is not available.
     */
    public int getLineNumber() {
        return super.getLineNumber();
    }

    /**
     * Returns the source file containing the field access.
     *
     * @return null     if this information is not available.
     */
    public String getFileName() {
        return super.getFileName();
    }


    /**
     * Returns the class in which the field is declared.
     */
    private CtClass getCtClass() throws NotFoundException {
        return thisClass.getClassPool().get(getClassName());
    }

    /**
     * Returns the name of the class in which the field is declared.
     */
    public String getClassName() {
        int index = iterator.u16bitAt(currentPos + 1);
        return getConstPool().getFieldrefClassName(index);
    }


    public InstructionHandle getInsn(int idx) {
    	if(handles == null || handles.length <= idx)
    		throw new RuntimeException("No instruction at index idx");
    	return handles[idx];
    }
    
    /**
     * Returns the list of exceptions that the expression may throw.
     * This list includes both the exceptions that the try-catch statements
     * including the expression can catch and the exceptions that
     * the throws declaration allows the method to throw.
     */
    public CtClass[] mayThrow() {
        return super.mayThrow();
    }


    /**
     * Replaces the matched instructions with the bytecode derived from
     * the given source text.
     *
     *
     * @param statement         a Java statement except try-catch.
     */
    public void replace(String statement) throws CannotCompileException {
        thisClass.getClassFile();   // to call checkModify().
        ConstPool constPool = getConstPool();
        int pos = currentPos;

        Javac jc = new Javac(thisClass);
        CodeAttribute ca = iterator.get();
        try {
            CtClass[] params;
            CtClass retType;
            params = new CtClass[0];

            jc.recordParams(params, withinStatic());


            Bytecode bytecode = jc.getBytecode();
            
            // Eventually, some stack alignment is required
            int stackValuesAdjustment = computeStackAdjustment(handles);

            if(stackValuesAdjustment < 0) {
            	for(int i = stackValuesAdjustment ; i <=0; i++)
            		bytecode.add(Opcode.POP);
            } else if(stackValuesAdjustment > 0) {
            	for(int i = stackValuesAdjustment ; i <=0; i++)
            		bytecode.add(Opcode.ICONST_0);            	
            }
//            bytecode.add(Opcode.POP);
//            storeStack(params, isStatic(), paramVar, bytecode);
            jc.recordLocalVariables(ca, pos);

            jc.compileStmnt(statement);

            // Replace bytecodes starting at position pos
            // by a new bytecode
            int matchedBytecodeLen = byteCodeLength(handles);
			System.out.println("Replacing matched bytecode at pos: " + pos
					+ " of length " + matchedBytecodeLen + " bytes");
            replace0(pos, bytecode, matchedBytecodeLen);
            iterator.move(iterator.lookAhead() + matchedBytecodeLen);
        }
        catch (CompileError e) { throw new CannotCompileException(e); }
        catch (BadBytecode e) {
            throw new CannotCompileException("broken method");
        }
    }

	private int computeStackAdjustment(InstructionHandle[] handles) {
		int stackChange = 0;
		for(InstructionHandle handle: handles) {
			int opcode = handle.getInstructionOpcode(iterator);
			stackChange -= consumesStack(opcode);
			stackChange += producesStack(opcode);
		}
		return stackChange;
	}

	private int producesStack(int opcode) {
		int produces = OpcodeInfo.PRODUCE_STACK[opcode];
		if(produces == OpcodeInfo.UNPREDICTABLE) {
			// TODO: Make it a bit more flexible :-)
			return 1;
		}
			
		return produces;
	}

	private int consumesStack(int opcode) {
		int consumes = OpcodeInfo.CONSUME_STACK[opcode];
		if(consumes == OpcodeInfo.UNPREDICTABLE) {
			// TODO: Make it a bit more flexible :-)
			return 1;
		}
			
		return consumes;
	}

	private int byteCodeLength(InstructionHandle[] handles) {
		int len = 0;
		for(InstructionHandle handle: handles){
			len += handle.length();
		}
		return len;
	}

	public InstructionHandle[] getMatch() {
		return handles;
	}
	
	public CodeIterator getIterator() {
		return iterator; 
	}
}
