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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.Mnemonic;
import javassist.bytecode.Opcode;
import javassist.bytecode.OpcodeInfo;


/**
 * InstructionFinder is a tool to search for given instructions patterns, i.e.,
 * match sequences of instructions in an instruction list via regular
 * expressions. This can be used, e.g., in order to implement a peep hole
 * optimizer that looks for code patterns and replaces them with faster
 * equivalents.
 * 
 * <p>
 * This class internally uses the java.util.regex
 * package to search for regular expressions.
 * 
 * A typical application would look like this:
 * 
 * <pre>
 * 
 *  
 *   InstructionFinder f   = new InstructionFinder(il);
 *   String            pat = &quot;IfInstruction ICONST_0 GOTO ICONST_1 NOP (IFEQ|IFNE)&quot;;
 *   
 *   for(Iterator i = f.search(pat, constraint); i.hasNext(); ) {
 *   InstructionHandle[] match = (InstructionHandle[])i.next();
 *   ...
 *   il.delete(match[1], match[5]);
 *   ...
 *   }
 *   
 *  
 * </pre>
 * 
 * @version $Id$
 * @author <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @author <A HREF="mailto:m.romixlev@gmail.com">Roman Levenstein</A> adaptation for Javassist
 */
public class InstructionFinder {

    private static final int OFFSET = 32767; // char + OFFSET is
    // outside of
    // LATIN-1
    private static final int NO_OPCODES = 256; // Potential number,
    // some are not used
    private static final Map<String, String> map = new HashMap<String, String>();
    private CodeIterator il;
    private String il_string; // instruction list
    // as string
    private InstructionHandle[] handles; // map instruction
    
    public static class InstructionHandle {
    	private int start;
    	private int end;
    	
    	public InstructionHandle(int start, int end) {
    		this.start = start;
    		this.end = end;
    	}
    	
    	public InstructionHandle(InstructionHandle handle) {
    		this.start = handle.start;
    		this.end = handle.end;
    	}
    	
    	public int getInstructionOpcode(CodeIterator il) {
    		return il.byteAt(start);
    	}

		public int length() {
			return end-start;
		}

		public int getStart() {
			return start;
		}

		public int getEnd() {
			return end;
		}

    }


    // list to array
    /**
     * @param il
     *          instruction list to search for given patterns
     */
    public InstructionFinder(CodeIterator il) {
        this.il = il.get().iterator();
        reread();
    }


    /**
     * Reread the instruction list, e.g., after you've altered the list upon a
     * match.
     */
    public final void reread() {
    	il = il.get().iterator();
        int size = il.getCodeLength();
        char[] buf = new char[size]; // Create a string with length equal to il
        // length
        try {
			handles = getInstructionHandles(il.get().iterator());
		} catch (BadBytecode e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        // Map opcodes to characters
        for (int i = 0; i < handles.length; i++) {
            buf[i] = makeChar(handles[i].getInstructionOpcode(il));
        }
        il_string = new String(buf);
    }


    private InstructionHandle[] getInstructionHandles(CodeIterator il) throws BadBytecode {
    	int insnNum = 0;
    	Vector<InstructionHandle> handles = new Vector<InstructionFinder.InstructionHandle>();
    	il.begin();
    	while(il.hasNext()) {
    		insnNum++;
    		int start = il.lookAhead();
    		il.next();
    		int end = il.lookAhead();
    		handles.add(new InstructionHandle(start, end));
//    		System.out.println("new handle for insn: " + start + ", " + end + " : " + Mnemonic.OPCODE[il.byteAt(start)]);
    	}
		return handles.toArray(new InstructionHandle[] {});
	}


	/**
     * Map symbolic instruction names like "getfield" to a single character.
     * 
     * @param pattern
     *          instruction pattern in lower case
     * @return encoded string for a pattern such as "BranchInstruction".
     */
    private static final String mapName( String pattern ) {
        String result = map.get(pattern);
        if (result != null) {
            return result;
        }
        for (short i = 0; i < NO_OPCODES; i++) {
            if (pattern.equals(Mnemonic.OPCODE[i])) {
                return "" + makeChar(i);
            }
        }
        throw new RuntimeException("Instruction unknown: " + pattern);
    }


    /**
     * Replace symbolic names of instructions with the appropiate character and
     * remove all white space from string. Meta characters such as +, * are
     * ignored.
     * 
     * @param pattern
     *          The pattern to compile
     * @return translated regular expression string
     */
    public static final String compilePattern( String pattern ) {
        //Bug: 38787 - Instructions are assumed to be english, to avoid odd Locale issues
        String lower = pattern.toLowerCase(Locale.ENGLISH);
        StringBuilder buf = new StringBuilder();
        int size = pattern.length();
        for (int i = 0; i < size; i++) {
            char ch = lower.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                StringBuilder name = new StringBuilder();
                while ((Character.isLetterOrDigit(ch) || ch == '_') && i < size) {
                    name.append(ch);
                    if (++i < size) {
                        ch = lower.charAt(i);
                    } else {
                        break;
                    }
                }
                i--;
                buf.append(mapName(name.toString()));
            } else if (!Character.isWhitespace(ch)) {
                buf.append(ch);
            }
        }
        return buf.toString();
    }

    public static final Pattern compilePatternToRegex( String pattern ) {
    	return Pattern.compile(compilePattern(pattern));
    }
    
    

    /**
     * @return the matched piece of code as an array of instruction (handles)
     */
    private InstructionHandle[] getMatch( int matched_from, int match_length ) {
        InstructionHandle[] match = new InstructionHandle[match_length];
        System.arraycopy(handles, matched_from, match, 0, match_length);
        return match;
    }


    /**
     * Search for the given pattern in the instruction list. You can search for
     * any valid opcode via its symbolic name, e.g. "istore". You can also use a
     * super class or an interface name to match a whole set of instructions, e.g.
     * "BranchInstruction" or "LoadInstruction". "istore" is also an alias for all
     * "istore_x" instructions. Additional aliases are "if" for "ifxx", "if_icmp"
     * for "if_icmpxx", "if_acmp" for "if_acmpxx".
     * 
     * Consecutive instruction names must be separated by white space which will
     * be removed during the compilation of the pattern.
     * 
     * For the rest the usual pattern matching rules for regular expressions
     * apply.
     * <P>
     * Example pattern:
     * 
     * <pre>
     * search(&quot;BranchInstruction NOP ((IfInstruction|GOTO)+ ISTORE Instruction)*&quot;);
     * </pre>
     * 
     * <p>
     * If you alter the instruction list upon a match such that other matching
     * areas are affected, you should call reread() to update the finder and call
     * search() again, because the matches are cached.
     * 
     * @param pattern
     *          the instruction pattern to search for, where case is ignored
     * @param from
     *          where to start the search in the instruction list
     * @param constraint
     *          optional CodeConstraint to check the found code pattern for
     *          user-defined constraints
     * @return iterator of matches where e.nextElement() returns an array of
     *         instruction handles describing the matched area
     */
    public final Iterator<InstructionHandle[]> search( String pattern, InstructionHandle from, CodeConstraint constraint ) {
        String search = compilePattern(pattern);
        int start = -1;
        for (int i = 0; i < handles.length; i++) {
            if (handles[i] == from) {
            	// Where to start search from (index)
                start = i; 
                break;
            }
        }

        if (start == -1) {
            throw new RuntimeException("Instruction handle " + from
                    + " not found in instruction list.");
        }
        
        Pattern regex = Pattern.compile(search);
        List<InstructionHandle[]> matches = new ArrayList<InstructionHandle[]>();
        Matcher matcher = regex.matcher(il_string);
        while (start < il_string.length() && matcher.find(start)) {
            int startExpr = matcher.start();
            int endExpr = matcher.end();
            int lenExpr = (endExpr - startExpr);
            InstructionHandle[] match = getMatch(startExpr, lenExpr);
            if ((constraint == null) || constraint.checkCode(match)) {
                matches.add(match);
            }
            start = endExpr;
        }
        return matches.iterator();
    }

    public final InstructionHandle[] search(Pattern regex, int from, CodeConstraint constraint ) {        
        List<InstructionHandle[]> matches = new ArrayList<InstructionHandle[]>();
        Matcher matcher = regex.matcher(il_string);
        
        int handleIdx = 0;
        for(InstructionHandle handle: handles) {
        	if(handle.start == from)
        		break;
        	handleIdx ++;
        }

        int start = handleIdx;
        
        if(handleIdx >= handles.length)
        	throw new RuntimeException("Cannot find instruction starting at offset " + from);
        
        while (start < il_string.length() && matcher.find(start)) {
            int startExpr = matcher.start();
            int endExpr = matcher.end();
            int lenExpr = (endExpr - startExpr);
            InstructionHandle[] match = getMatch(startExpr, lenExpr);
            if ((constraint == null) || constraint.checkCode(match)) {
            	return match;
            }
            start = endExpr;
        }
        return null;
    }

    /**
     * Start search beginning from the start of the given instruction list.
     * 
     * @param pattern
     *          the instruction pattern to search for, where case is ignored
     * @return iterator of matches where e.nextElement() returns an array of
     *         instruction handles describing the matched area
     */
    public final Iterator<InstructionHandle[]> search( String pattern ) {
        return search(pattern, getStart(il), null);
    }


    private InstructionHandle getStart(CodeIterator il) {
		return handles[0];
	}


	/**
     * Start search beginning from `from'.
     * 
     * @param pattern
     *          the instruction pattern to search for, where case is ignored
     * @param from
     *          where to start the search in the instruction list
     * @return iterator of matches where e.nextElement() returns an array of
     *         instruction handles describing the matched area
     */
    public final Iterator<InstructionHandle[]> search( String pattern, InstructionHandle from ) {
        return search(pattern, from, null);
    }


    /**
     * Start search beginning from the start of the given instruction list. Check
     * found matches with the constraint object.
     * 
     * @param pattern
     *          the instruction pattern to search for, case is ignored
     * @param constraint
     *          constraints to be checked on matching code
     * @return instruction handle or `null' if the match failed
     */
    public final Iterator<InstructionHandle[]> search( String pattern, CodeConstraint constraint ) {
        return search(pattern, getStart(il), constraint);
    }


    /**
     * Convert opcode number to char.
     */
    private static final char makeChar( int opcode ) {
        return (char) (opcode + OFFSET);
    }


    /**
     * @return the inquired instruction list
     */
    public final CodeIterator getInstructionList() {
        return il;
    }

    /**
     * Code patterns found may be checked using an additional user-defined
     * constraint object whether they really match the needed criterion. I.e.,
     * check constraints that can not expressed with regular expressions.
     * 
     */
    public static interface CodeConstraint {

        /**
         * @param match
         *          array of instructions matching the requested pattern
         * @return true if the matched area is really useful
         */
        public boolean checkCode( InstructionHandle[] match );
    }

    // Initialize pattern map
    static {
        map.put("arithmeticinstruction","(irem|lrem|iand|ior|ineg|isub|lneg|fneg|fmul|ldiv|fadd|lxor|frem|idiv|land|ixor|ishr|fsub|lshl|fdiv|iadd|lor|dmul|lsub|ishl|imul|lmul|lushr|dneg|iushr|lshr|ddiv|drem|dadd|ladd|dsub)");
		map.put("invokeinstruction", "(invokevirtual|invokeinterface|invokestatic|invokespecial)");
		map.put("arrayinstruction", "(baload|aastore|saload|caload|fastore|lastore|iaload|castore|iastore|aaload|bastore|sastore|faload|laload|daload|dastore)");
		map.put("gotoinstruction", "(goto|goto_w)");
		map.put("conversioninstruction", "(d2l|l2d|i2s|d2i|l2i|i2b|l2f|d2f|f2i|i2d|i2l|f2d|i2c|f2l|i2f)");
		map.put("localvariableinstruction","(fstore|iinc|lload|dstore|dload|iload|aload|astore|istore|fload|lstore)");
		map.put("loadinstruction", "(fload|dload|lload|iload|aload)");
		map.put("fieldinstruction", "(getfield|putstatic|getstatic|putfield)");
		map.put("cpinstruction", "(ldc2_w|invokeinterface|multianewarray|putstatic|instanceof|getstatic|checkcast|getfield|invokespecial|ldc_w|invokestatic|invokevirtual|putfield|ldc|new|anewarray)");
		map.put("stackinstruction", "(dup2|swap|dup2_x2|pop|pop2|dup|dup2_x1|dup_x2|dup_x1)");
		map.put("branchinstruction", "(ifle|if_acmpne|if_icmpeq|if_acmpeq|ifnonnull|goto_w|iflt|ifnull|if_icmpne|tableswitch|if_icmple|ifeq|if_icmplt|jsr_w|if_icmpgt|ifgt|jsr|goto|ifne|ifge|lookupswitch|if_icmpge)");
		map.put("returninstruction", "(lreturn|ireturn|freturn|dreturn|areturn|return)");
		map.put("storeinstruction", "(istore|fstore|dstore|astore|lstore)");
		map.put("select", "(tableswitch|lookupswitch)");
		map.put("ifinstruction", "(ifeq|ifgt|if_icmpne|if_icmpeq|ifge|ifnull|ifne|if_icmple|if_icmpge|if_acmpeq|if_icmplt|if_acmpne|ifnonnull|iflt|if_icmpgt|ifle)");
		map.put("jsrinstruction", "(jsr|jsr_w)");
		map.put("variablelengthinstruction", "(tableswitch|jsr|goto|lookupswitch)");
		map.put("unconditionalbranch", "(goto|jsr|jsr_w|athrow|goto_w)");
		map.put("constantpushinstruction", "(dconst|bipush|sipush|fconst|iconst|lconst)");
		map.put("typedinstruction", "(imul|lsub|aload|fload|lor|new|aaload|fcmpg|iand|iaload|lrem|idiv|d2l|isub|dcmpg|dastore|ret|f2d|f2i|drem|iinc|i2c|checkcast|frem|lreturn|astore|lushr|daload|dneg|fastore|istore|lshl|ldiv|lstore|areturn|ishr|ldc_w|invokeinterface|aastore|lxor|ishl|l2d|i2f|return|faload|sipush|iushr|caload|instanceof|invokespecial|putfield|fmul|ireturn|laload|d2f|lneg|ixor|i2l|fdiv|lastore|multianewarray|i2b|getstatic|i2d|putstatic|fcmpl|saload|ladd|irem|dload|jsr_w|dconst|dcmpl|fsub|freturn|ldc|aconst_null|castore|lmul|ldc2_w|dadd|iconst|f2l|ddiv|dstore|land|jsr|anewarray|dmul|bipush|dsub|sastore|d2i|i2s|lshr|iadd|l2i|lload|bastore|fstore|fneg|iload|fadd|baload|fconst|ior|ineg|dreturn|l2f|lconst|getfield|invokevirtual|invokestatic|iastore)");
		map.put("popinstruction", "(fstore|dstore|pop|pop2|astore|putstatic|istore|lstore)");
		map.put("allocationinstruction", "(multianewarray|new|anewarray|newarray)");
		map.put("indexedinstruction", "(lload|lstore|fload|ldc2_w|invokeinterface|multianewarray|astore|dload|putstatic|instanceof|getstatic|checkcast|getfield|invokespecial|dstore|istore|iinc|ldc_w|ret|fstore|invokestatic|iload|putfield|invokevirtual|ldc|new|aload|anewarray)");
		map.put("pushinstruction", "(dup|lload|dup2|bipush|fload|ldc2_w|sipush|lconst|fconst|dload|getstatic|ldc_w|aconst_null|dconst|iload|ldc|iconst|aload)");
		map.put("stackproducer", "(imul|lsub|aload|fload|lor|new|aaload|fcmpg|iand|iaload|lrem|idiv|d2l|isub|dcmpg|dup|f2d|f2i|drem|i2c|checkcast|frem|lushr|daload|dneg|lshl|ldiv|ishr|ldc_w|invokeinterface|lxor|ishl|l2d|i2f|faload|sipush|iushr|caload|instanceof|invokespecial|fmul|laload|d2f|lneg|ixor|i2l|fdiv|getstatic|i2b|swap|i2d|dup2|fcmpl|saload|ladd|irem|dload|jsr_w|dconst|dcmpl|fsub|ldc|arraylength|aconst_null|tableswitch|lmul|ldc2_w|iconst|dadd|f2l|ddiv|land|jsr|anewarray|dmul|bipush|dsub|d2i|newarray|i2s|lshr|iadd|lload|l2i|fneg|iload|fadd|baload|fconst|lookupswitch|ior|ineg|lconst|l2f|getfield|invokevirtual|invokestatic)");
		map.put("stackconsumer", "(imul|lsub|lor|iflt|fcmpg|if_icmpgt|iand|ifeq|if_icmplt|lrem|ifnonnull|idiv|d2l|isub|dcmpg|dastore|if_icmpeq|f2d|f2i|drem|i2c|checkcast|frem|lreturn|astore|lushr|pop2|monitorexit|dneg|fastore|istore|lshl|ldiv|lstore|areturn|if_icmpge|ishr|monitorenter|invokeinterface|aastore|lxor|ishl|l2d|i2f|return|iushr|instanceof|invokespecial|fmul|ireturn|d2f|lneg|ixor|pop|i2l|ifnull|fdiv|lastore|i2b|if_acmpeq|ifge|swap|i2d|putstatic|fcmpl|ladd|irem|dcmpl|fsub|freturn|ifgt|castore|lmul|dadd|f2l|ddiv|dstore|land|if_icmpne|if_acmpne|dmul|dsub|sastore|ifle|d2i|i2s|lshr|iadd|l2i|bastore|fstore|fneg|fadd|ior|ineg|ifne|dreturn|l2f|if_icmple|getfield|invokevirtual|invokestatic|iastore)");
		map.put("exceptionthrower","(irem|lrem|laload|putstatic|baload|dastore|areturn|getstatic|ldiv|anewarray|iastore|castore|idiv|saload|lastore|fastore|putfield|lreturn|caload|getfield|return|aastore|freturn|newarray|instanceof|multianewarray|athrow|faload|iaload|aaload|dreturn|monitorenter|checkcast|bastore|arraylength|new|invokevirtual|sastore|ldc_w|ireturn|invokespecial|monitorexit|invokeinterface|ldc|invokestatic|daload)");
		map.put("loadclass", "(multianewarray|invokeinterface|instanceof|invokespecial|putfield|checkcast|putstatic|invokevirtual|new|getstatic|invokestatic|getfield|anewarray)");
		map.put("instructiontargeter", "(ifle|if_acmpne|if_icmpeq|if_acmpeq|ifnonnull|goto_w|iflt|ifnull|if_icmpne|tableswitch|if_icmple|ifeq|if_icmplt|jsr_w|if_icmpgt|ifgt|jsr|goto|ifne|ifge|lookupswitch|if_icmpge)");
		// Some aliases
		map.put("if_icmp", "(if_icmpne|if_icmpeq|if_icmple|if_icmpge|if_icmplt|if_icmpgt)");
		map.put("if_acmp", "(if_acmpeq|if_acmpne)");
		map.put("if", "(ifeq|ifne|iflt|ifge|ifgt|ifle)");
		// Precompile some aliases first
		map.put("iconst", precompile(Opcode.ICONST_0, Opcode.ICONST_5, Opcode.ICONST_M1));
		map.put("lconst", new String(new char[] { '(', makeChar(Opcode.LCONST_0), '|', makeChar(Opcode.LCONST_1), ')' }));
		map.put("dconst", new String(new char[] { '(', makeChar(Opcode.DCONST_0), '|', makeChar(Opcode.DCONST_1), ')' }));
		map.put("fconst", new String(new char[] { '(', makeChar(Opcode.FCONST_0), '|', makeChar(Opcode.FCONST_1), ')' }));
		map.put("iload", precompile(Opcode.ILOAD_0, Opcode.ILOAD_3, Opcode.ILOAD));
		map.put("dload", precompile(Opcode.DLOAD_0, Opcode.DLOAD_3, Opcode.DLOAD));
		map.put("fload", precompile(Opcode.FLOAD_0, Opcode.FLOAD_3, Opcode.FLOAD));
		map.put("aload", precompile(Opcode.ALOAD_0, Opcode.ALOAD_3, Opcode.ALOAD));
		map.put("istore", precompile(Opcode.ISTORE_0, Opcode.ISTORE_3, Opcode.ISTORE));
		map.put("dstore", precompile(Opcode.DSTORE_0, Opcode.DSTORE_3, Opcode.DSTORE));
		map.put("fstore", precompile(Opcode.FSTORE_0, Opcode.FSTORE_3, Opcode.FSTORE));
		map.put("astore", precompile(Opcode.ASTORE_0, Opcode.ASTORE_3, Opcode.ASTORE));
		// Compile strings
		for (String key : map.keySet()) {
			String value = map.get(key);
			// Omit already precompiled patterns
			char ch = value.charAt(1); 
			if (ch < OFFSET) {
				// precompile all patterns
				map.put(key, compilePattern(value)); 
			}
		}
		// Add instruction alias to match anything
		StringBuilder buf = new StringBuilder("(");
		for (short i = 0; i < NO_OPCODES; i++) {
			if (OpcodeInfo.NO_OF_OPERANDS[i] != OpcodeInfo.UNDEFINED) { 
				// Not an invalid opcode
				buf.append(makeChar(i));
				if (i < NO_OPCODES - 1) {
					buf.append('|');
				}
			}
		}
		buf.append(')');
		map.put("instruction", buf.toString());
    }


    private static String precompile( int from, int to, int extra ) {
        StringBuilder buf = new StringBuilder("(");
        for (int i = from; i <= to; i++) {
            buf.append(makeChar(i));
            buf.append('|');
        }
        buf.append(makeChar(extra));
        buf.append(")");
        return buf.toString();
    }

    /*
	 * Internal debugging routines.
	 */
//    private static final String pattern2string( String pattern ) {
//        return pattern2string(pattern, true);
//    }


//    private static final String pattern2string( String pattern, boolean make_string ) {
//        StringBuffer buf = new StringBuffer();
//        for (int i = 0; i < pattern.length(); i++) {
//            char ch = pattern.charAt(i);
//            if (ch >= OFFSET) {
//                if (make_string) {
//                    buf.append(Opcode.OPCODE_NAMES[ch - OFFSET]);
//                } else {
//                    buf.append((ch - OFFSET));
//                }
//            } else {
//                buf.append(ch);
//            }
//        }
//        return buf.toString();
//    }

    
    public static class Test {
    	int start;
    	int end;
    	
    	public Test(int start, int end) {
    		this.start = start;
    		this.end = end;
    	}
    	
    	public Test(Test handle) {
    		System.out.println("Constructor");
    		this.start = handle.start;
    		this.end = getTest().end;
    	}
    	
    	private Test getTest() {
    		return null;
    	}
    	
    	int getInstructionOpcode(CodeIterator il) {
    		return il.byteAt(start);
    	}

		public int length() {
			return end-start;
		}
    }    
}
