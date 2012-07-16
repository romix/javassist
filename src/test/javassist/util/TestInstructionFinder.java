/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 *
 */
package javassist.util;

import java.util.Iterator;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Mnemonic;
import javassist.expr.FieldAccess;
import javassist.expr.InsnSequenceEditor;
import javassist.expr.InsnSequenceExpr;
import javassist.util.InstructionFinder.CodeConstraint;
import javassist.util.InstructionFinder.InstructionHandle;
import junit.framework.TestCase;

public class TestInstructionFinder extends TestCase {

    static class FieldAccessInsn extends FieldAccess {
    	// Make FieldAccess constructor visible
		public FieldAccessInsn(int pos, CodeIterator i, CtClass declaring,
				MethodInfo m, int op) {
			super(pos, i, declaring, m, op);
			// TODO Auto-generated constructor stub
		}
    }

	public void testFindGetFieldPutField() throws NotFoundException {
		ClassPool pool = ClassPool.getDefault();
		final CtClass clazz = pool.get("javassist.util.InstructionFinder$InstructionHandle");

		String pat = "GETFIELD PUTFIELD";
		CtBehavior[] constructors = clazz.getConstructors();
		findInstructionsSequence(clazz, constructors, pat);		
		CtBehavior[] methods = clazz.getMethods();
		findInstructionsSequence(clazz, methods, pat);		
	}

	/***
	 * Find a given bytecode sequence pattern by inspecting provided methods.
	 * Use raw {@link InstructionFinder} API.
	 *  
	 * @param clazz class to be inspected
	 * @param methods methods to be inspected
	 * @param pat bytecode instructions pattern to search for  
	 */
	private void findInstructionsSequence(final CtClass clazz,
			CtBehavior[] methods, String pat) {
		for(final CtBehavior method: methods) {
			final MethodInfo methodInfo = method.getMethodInfo(); 
			CodeAttribute ca = methodInfo.getCodeAttribute();
			if(ca == null)
				continue;
			System.out.println("Inspecting " +  method.getName());
			final CodeIterator it = ca.iterator();
			InstructionFinder f = new InstructionFinder(it);

			// Define a constraint to check when a bytecode instruction sequence is found
			CodeConstraint constraint = new CodeConstraint() {				
				public boolean checkCode(InstructionHandle[] match) {
					System.out.println("Checking constraints");
					FieldAccessInsn fieldAccessInsn1 = new FieldAccessInsn(
							match[0].getStart(), it, clazz,
							methodInfo,
							match[0].getInstructionOpcode(it));

					FieldAccessInsn fieldAccessInsn2 = new FieldAccessInsn(
							match[1].getStart(), it, clazz,
							methodInfo,
							match[1].getInstructionOpcode(it));
					
					try {
						if(!fieldAccessInsn1.getField().equals(fieldAccessInsn2.getField()))
							return false;
					} catch (NotFoundException e) {
						throw new RuntimeException(e);
					}
					
					for (InstructionHandle handle : match) {
						FieldAccessInsn fieldAccessInsn = new FieldAccessInsn(
								handle.getStart(), it, clazz,
								methodInfo,
								handle.getInstructionOpcode(it));
						if (fieldAccessInsn.isReader()) {
							System.out.println("Reading field "
									+ fieldAccessInsn.getFieldName());
						} else {
							System.out.println("Writing field "
									+ fieldAccessInsn.getFieldName());
						}
					}
					for (InstructionHandle handle : match) {
						System.out.println("Insn: "
								+ Mnemonic.OPCODE[handle
										.getInstructionOpcode(it)]);
					}
					return true;
				}
			};
			
			for (Iterator i = f.search(pat, (CodeConstraint) constraint); i.hasNext();) {
				InstructionHandle[] match = (InstructionHandle[]) i.next();
				System.out.println("Found");
				for(InstructionHandle handle: match) {
					System.out.println("Insn: " + Mnemonic.OPCODE[handle.getInstructionOpcode(it)]);
				}
			}
		}
	}

	public void testInsnSequenceEditor() throws NotFoundException, CannotCompileException {
		ClassPool pool = ClassPool.getDefault();
		final CtClass clazz = pool.get("javassist.util.InstructionFinder$InstructionHandle");

		String pat = "GETFIELD PUTFIELD";
		
		CtBehavior[] constructors = clazz.getConstructors();
		findInsnSequenceExpr(clazz, constructors, pat);		
		CtBehavior[] methods = clazz.getMethods();
		findInsnSequenceExpr(clazz, methods, pat);		
	}

	/***
	 * Find a given bytecode sequence pattern by inspecting provided methods.
	 * Use {@link InsnSequenceEditor} and {@link InsnSequenceExpr} APIs.
	 *  
	 * @param clazz class to be inspected
	 * @param methods methods to be inspected
	 * @param pat bytecode instructions pattern to search for  
	 */
	private void findInsnSequenceExpr(final CtClass clazz, CtBehavior[] methods,
			String pat) throws CannotCompileException {
		
		
		for(final CtBehavior method: methods) {			
			final MethodInfo methodInfo = method.getMethodInfo(); 
			CodeAttribute ca = methodInfo.getCodeAttribute();
			if(ca == null)
				continue;
			
			System.out.println("Inspecting " +  method.getName());
			final CodeIterator it = ca.iterator();
			
			// Define a constraint to check when a bytecode instruction sequence is found
			CodeConstraint constraint = new CodeConstraint() {				
				public boolean checkCode(InstructionHandle[] match) {
					System.out.println("Checking constraints");
					FieldAccessInsn fieldAccessInsn1 = new FieldAccessInsn(
							match[0].getStart(), it, clazz,
							methodInfo,
							match[0].getInstructionOpcode(it));

					FieldAccessInsn fieldAccessInsn2 = new FieldAccessInsn(
							match[1].getStart(), it, clazz,
							methodInfo,
							match[1].getInstructionOpcode(it));
					
					try {
						if(!fieldAccessInsn1.getField().equals(fieldAccessInsn2.getField()))
							return false;
					} catch (NotFoundException e) {
						throw new RuntimeException(e);
					}
					
					for (InstructionHandle handle : match) {
						FieldAccessInsn fieldAccessInsn = new FieldAccessInsn(
								handle.getStart(), it, clazz,
								methodInfo,
								handle.getInstructionOpcode(it));
						if (fieldAccessInsn.isReader()) {
							System.out.println("Reading field "
									+ fieldAccessInsn.getFieldName());
						} else {
							System.out.println("Writing field "
									+ fieldAccessInsn.getFieldName());
						}
					}
					for (InstructionHandle handle : match) {
						System.out.println("Insn: "
								+ Mnemonic.OPCODE[handle
										.getInstructionOpcode(it)]);
					}
					return true;
				}
			};
			
			// Define a callback to be called when instruction sequence is found
			InsnSequenceEditor editor = new InsnSequenceEditor(pat, constraint) {
				@Override
				public void edit(InsnSequenceExpr e)
						throws CannotCompileException {
					System.out.println("Found at position " + e.getMatch()[0].getStart());
					for(InstructionHandle handle: e.getMatch()) {
						System.out.println("Insn: " + Mnemonic.OPCODE[handle.getInstructionOpcode(it)]);
					}
					super.edit(e);
				}
				
			};
			
			method.instrument(editor);			
		}
		
	}
}
