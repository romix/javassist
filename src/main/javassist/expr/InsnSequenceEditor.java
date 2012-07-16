/*
 * Javassist, a Java-bytecode translator toolkit.
 * Copyright (C) 2012- Roman Levenstein. All Rights Reserved.
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

import java.util.regex.Pattern;

import javassist.bytecode.*;
import javassist.util.InstructionFinder;
import javassist.util.InstructionFinder.CodeConstraint;
import javassist.util.InstructionFinder.InstructionHandle;
import javassist.CtClass;
import javassist.CannotCompileException;

/**
 * A translator of method bodies.
 *
 * <p>The users can define a subclass of this class to customize how to
 * modify a method body.  The overall architecture is similar to the
 * strategy pattern.
 *
 * <p>If <code>instrument()</code> is called in
 * <code>CtMethod</code>, the method body is scanned from the beginning
 * to the end.
 * Whenever a byte-code instruction sequence defined by a regex pattern
 * is found, <code>edit()</code> is called in <code>InsnSequenceEditor</code>.
 * <code>edit()</code> can inspect and modify the given byte-code instructions sequence.
 * The modification is reflected on the original method body.  If
 * <code>edit()</code> does nothing, the original method body is not
 * changed.
 *
 * <p>The following code is an example:
 *
 * <ul><pre>
 * CtMethod cm = ...;
 * cm.instrument(new InsnSequenceEditor("GETFIELD PUTFIELD") {
 *     public void edit(InsnSequenceExpr e) throws CannotCompileException {
 *     		...
 *     }
 * });
 * </pre></ul>
 *
 * <p>This code inspects all method calls appearing in the method represented
 * by <code>cm</code> and it prints the names and the line numbers of the
 * methods declared in class <code>Point</code>.  This code does not modify
 * the body of the method represented by <code>cm</code>.  If the method
 * body must be modified, call <code>replace()</code>
 * in <code>MethodCall</code>.
 *
 * @see javassist.CtClass#instrument(InsnSequenceEditor)
 * @see javassist.CtMethod#instrument(InsnSequenceEditor)
 * @see javassist.CtConstructor#instrument(InsnSequenceEditor)
 * @see MethodCall
 * @see NewExpr
 * @see FieldAccess
 *
 * @see javassist.CodeConverter
 */
public class InsnSequenceEditor {
	
	private InstructionFinder insnFinder;
	final private String pattern;
	final private Pattern regex;
	private CodeConstraint constraint;
	
    /**
     * @param pattern pattern describing the instruction sequence to be searched for
     * @param constraint optional constraint which needs to be satisfied when a sequence matching pattern was found
     */
    public InsnSequenceEditor(String pattern, InstructionFinder.CodeConstraint constraint) {
    	this.pattern = pattern;
    	this.regex = InstructionFinder.compilePatternToRegex(pattern);
    	this.constraint = constraint;
	}

    /**
     * @param pattern pattern describing the instruction sequence to be searched for
     */
    public InsnSequenceEditor(String pattern) {
    	this(pattern, null);
	}
    
    /**
     * Undocumented method.  Do not use; internal-use only.
     */
    public boolean doit(CtClass clazz, MethodInfo minfo)
        throws CannotCompileException
    {
        CodeAttribute codeAttr = minfo.getCodeAttribute();
        if (codeAttr == null)
            return false;

        CodeIterator iterator = codeAttr.iterator();
        
        System.out.println("Processing " + clazz.getName()+"#"+minfo.getName());
        insnFinder = new InstructionFinder(iterator);
        
        boolean edited = false;
        LoopContext context = new LoopContext(codeAttr.getMaxLocals());

        while (iterator.hasNext())
            if (loopBody(iterator, clazz, minfo, context))
                edited = true;

        // codeAttr might be modified by other partiess
        // so I check the current value of max-locals.
        if (codeAttr.getMaxLocals() < context.maxLocals)
            codeAttr.setMaxLocals(context.maxLocals);

        codeAttr.setMaxStack(codeAttr.getMaxStack() + context.maxStack);
        try {
            if (edited)
                minfo.rebuildStackMapIf6(clazz.getClassPool(),
                                         clazz.getClassFile2());
        }
        catch (BadBytecode b) {
            throw new CannotCompileException(b.getMessage(), b);
        }

        return edited;
    }

    /**
     * Visits each bytecode in the given range. 
     */
    boolean doit(CtClass clazz, MethodInfo minfo, LoopContext context,
                 CodeIterator iterator, int endPos)
        throws CannotCompileException
    {
        System.out.println("Processing " + clazz.getName()+"#"+minfo.getName());
    	insnFinder = new InstructionFinder(iterator);

        boolean edited = false;
        while (iterator.hasNext() && iterator.lookAhead() < endPos) {
            int size = iterator.getCodeLength();
            if (loopBody(iterator, clazz, minfo, context)) {
                edited = true;
                int size2 = iterator.getCodeLength();
                if (size != size2)  // the body was modified.
                    endPos += size2 - size;
            }
        }

        return edited;
    }


    final static class LoopContext {
        int maxLocals;
        int maxStack;

        LoopContext(int locals) {
            maxLocals = locals;
            maxStack = 0;
        }

        void updateMax(int locals, int stack) {
            if (maxLocals < locals)
                maxLocals = locals;

            if (maxStack < stack)
                maxStack = stack;
        }
    }

    final boolean loopBody(CodeIterator iterator, CtClass clazz,
                           MethodInfo minfo, LoopContext context)
        throws CannotCompileException
    {
        int nextPos; 
        try {
            Expr expr = null;
            int pos = iterator.next();
            nextPos = iterator.lookAhead(); 
            int c = iterator.byteAt(pos);
            // Find the next occurrence of the instruction sequence 
            InstructionHandle[] match = insnFinder.search(regex, pos, constraint);
            
            if(match != null) {
            	int matchPos = match[0].getStart(); 
                nextPos = match[0].getEnd(); 
            	iterator.move(matchPos);
            	expr = new InsnSequenceExpr(matchPos, iterator, clazz, minfo, match);
            	edit((InsnSequenceExpr)expr);
            } else {
            	return false;
            }
            
            if (expr != null && expr.edited()) {
//            	int newPos = expr.iterator.lookAhead() ;
//            	System.out.println("Continue after replace from position " + newPos);
//            	iterator.move(newPos);
            	insnFinder.reread();
            	// TODO: Compute a proper position for iterator, so that it does not scan the same
            	// byte code sequence again.
                context.updateMax(expr.locals(), expr.stack());
                return true;
            }
            else {
            	iterator.move(nextPos);
                return false;
            }
        }
        catch (BadBytecode e) {
            throw new CannotCompileException(e);
        }
    }

    /**
     * Edits a <tt>InstructionSequence</tt> expression (overridable).
     * The default implementation performs nothing.
     *
     * @param e         the <tt>instruction sequence</tt> matching a pattern.
     */
    public void edit(InsnSequenceExpr e) throws CannotCompileException {}
}
