package co.streamx.fluent.extree.expression;

import java.io.Serializable;
import java.lang.classfile.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.*;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import co.streamx.fluent.extree.expression.ExpressionStack.BranchExpression;

/**
 *
 */

final class ExpressionMethodVisitor //extends MethodVisitor 
{

    private static final Class<?>[] NumericTypeLookup = new Class<?>[]{Integer.TYPE, Long.TYPE, Float.TYPE,
            Double.TYPE};
    private static final Class<?>[] NumericTypeLookup2 = new Class<?>[]{Byte.TYPE, Character.TYPE, Short.TYPE};
    private static final String LambdaMetafactoryClassInternalName = "L" + LambdaMetafactory.class.getName()
            .replace('.', '/') + ";";

    private static final Map<Class<?>, Class<?>> _primitives;
    private static final Class<?>[] arrayTypesByCode = new Class[]{Boolean.TYPE, Character.TYPE, Float.TYPE,
            Double.TYPE, Byte.TYPE, Short.TYPE, Integer.TYPE, Long.TYPE};

    private ExpressionStack _exprStack;
    private List<Expression> _statements;
    private Expression[] _localVariables;

    private final HashMap<Label, List<ExpressionStack>> _branches = new HashMap<Label, List<ExpressionStack>>();

    private final ExpressionResolver _classVisitor;
    private final Class<?>[] _argTypes;
    private final Expression _me;

    static {
        Map<Class<?>, Class<?>> primitives = new HashMap<Class<?>, Class<?>>();
        primitives.put(Boolean.class, Boolean.TYPE);
        primitives.put(Byte.class, Byte.TYPE);
        primitives.put(Character.class, Character.TYPE);
        primitives.put(Double.class, Double.TYPE);
        primitives.put(Float.class, Float.TYPE);
        primitives.put(Integer.class, Integer.TYPE);
        primitives.put(Long.class, Long.TYPE);
        primitives.put(Short.class, Short.TYPE);

        // primitives.put(BigInteger.class, BigInteger.class);
        // primitives.put(BigDecimal.class, BigDecimal.class);
        //
        // primitives.put(String.class, String.class);
        // primitives.put(Class.class, Class.class);

        _primitives = primitives;
    }

    ExpressionMethodVisitor(ExpressionResolver classVisitor, Expression me, Class<?>[] argTypes) {
        _classVisitor = classVisitor;
        _me = me;
        _argTypes = argTypes;
    }

    private static Class<?> normalizePrimitive(Class<?> clz) {
        Class<?> primitive = _primitives.get(clz);
        return primitive != null ? primitive : clz;
    }

    private List<ExpressionStack> getBranchUsers(Label label) {
        List<ExpressionStack> bl = _branches.get(label);
        if (bl == null) {
            bl = new ArrayList<ExpressionStack>();
            _branches.put(label, bl);
        }

        return bl;
    }

    private void go(Label label) {

        getBranchUsers(label).add(_exprStack);

        _exprStack = null;
    }

    private void branch(Label label,
                        Expression test) {
        List<ExpressionStack> bl = getBranchUsers(label);

        ExpressionStack.BranchExpression br = new ExpressionStack.BranchExpression(_exprStack, test);
        _exprStack.push(br);

        ExpressionStack left = br.getFalse();
        bl.add(left);
        _exprStack = br.getTrue();
    }

    private void pushZeroConstantOrReduce() {
        Expression e = _exprStack.peek();
        if (e.getExpressionType() == ExpressionType.Subtract) {// reduce
            BinaryExpression be = (BinaryExpression) _exprStack.pop();
            _exprStack.push(be.getFirst());
            _exprStack.push(be.getSecond());

            return;
        }
        Class<?> type = _exprStack.peek().getResultType();
        Object value;

        if (type == Byte.TYPE)
            value = Byte.valueOf((byte) 0);
        else if (type == Double.TYPE)
            value = Double.valueOf(0d);
        else if (type == Float.TYPE)
            value = Float.valueOf(0f);
        else if (type == Integer.TYPE)
            value = Integer.valueOf(0);
        else if (type == Long.TYPE)
            value = Long.valueOf(0l);
        else if (type == Short.TYPE)
            value = Short.valueOf((short) 0);
        else if (type == Boolean.TYPE)
            value = Boolean.FALSE;
        else
            throw new IllegalStateException(type.toString());

        _exprStack.push(Expression.constant(value, type));
    }

    //@Override
    public void visitCode() {
        _exprStack = new ExpressionStack();
    }

    //@Override
    public void visitEnd() {

        visitLabel(null);

        if (_exprStack.isEmpty()) {
            assert _classVisitor.getType() == Void.TYPE;
        } else {
            if (_classVisitor.getType() == Void.TYPE) {
                if (_statements == null)
                    _statements = new ArrayList<>(_exprStack);
                else {
                    _statements.addAll(_exprStack);
                    _exprStack.sort(_statements);
                }
            } else {
                assert _exprStack.size() == 1;
                _classVisitor.setResult(_exprStack.pop());
            }
        }

        _classVisitor.setStatements(_statements);

        _classVisitor.setLocals(_localVariables != null ? Collections.unmodifiableList(Arrays.asList(_localVariables))
                : Collections.emptyList());
    }

    //@Override
    public void visitFieldInsn(int opcode,
                               ClassEntry owner,
                               String name,
                               String desc) {
        Expression e;
        boolean isSyntheticConstant = false;
        switch (opcode) {
            case ClassFile.GETFIELD:
                Expression instance = _exprStack.pop();
                try {
                    e = Expression.get(instance, name);
                } catch (NoSuchFieldException nsfe) {
                    throw new RuntimeException(nsfe);
                }
                if (instance.getExpressionType() == ExpressionType.Constant && instance.getResultType().isSynthetic())
                    isSyntheticConstant = true;
                break;
            case ClassFile.GETSTATIC:
                try {
                    Class<?> containingClass = _classVisitor.getClass(Signature.ClassTypeSig.of(owner.asInternalName()));
                    e = Expression.get(containingClass, name);
                    if (containingClass.isSynthetic())
                        isSyntheticConstant = true;
                } catch (NoSuchFieldException nsfe) {
                    throw new RuntimeException(nsfe);
                }
                break;
            case ClassFile.PUTFIELD:
            case ClassFile.PUTSTATIC:
            default:
                throw notLambda(opcode);
        }

        if (isSyntheticConstant) {
            // evaluate now, since has no meaning to the user in the field form
            Object value = e.accept(Interpreter.Instance).apply(null);
            e = Expression.constant(value, e.getResultType());
        }

        _exprStack.push(e);
    }

    //@Override
    public void visitFrame(int type,
                           int nLocal,
                           Object[] local,
                           int nStack,
                           Object[] stack) {
        throw notLambda(type);
    }

    //@Override
    public void visitIincInsn(int arg0,
                              int arg1) {
        throw notLambda(ClassFile.IINC);
    }

    //@Override
    public void visitInsn(int opcode) {
        Expression e;
        Expression first;
        Expression second;
        switch (opcode) {
            case ClassFile.ARRAYLENGTH:
                e = Expression.arrayLength(_exprStack.pop());
                break;
            case ClassFile.ACONST_NULL:
                e = Expression.constant(null, Object.class);
                break;
            case ClassFile.IALOAD:
            case ClassFile.LALOAD:
            case ClassFile.FALOAD:
            case ClassFile.DALOAD:
            case ClassFile.AALOAD:
            case ClassFile.BALOAD:
            case ClassFile.CALOAD:
            case ClassFile.SALOAD:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.arrayIndex(second, first);
                break;
            case ClassFile.DCONST_0:
                e = Expression.constant(0d, Double.TYPE);
                break;
            case ClassFile.DCONST_1:
                e = Expression.constant(1d, Double.TYPE);
                break;
            case ClassFile.FCMPG:
            case ClassFile.FCMPL:
            case ClassFile.DCMPG:
            case ClassFile.DCMPL:
            case ClassFile.LCMP:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.subtract(second, first);
                break;
            case ClassFile.FCONST_0:
                e = Expression.constant(0f, Float.TYPE);
                break;
            case ClassFile.FCONST_1:
                e = Expression.constant(1f, Float.TYPE);
                break;
            case ClassFile.FCONST_2:
                e = Expression.constant(2f, Float.TYPE);
                break;
            case ClassFile.ICONST_M1:
                e = Expression.constant(-1, Integer.TYPE);
                break;
            case ClassFile.ICONST_0:
                e = Expression.constant(0, Integer.TYPE);
                break;
            case ClassFile.ICONST_1:
                e = Expression.constant(1, Integer.TYPE);
                break;
            case ClassFile.ICONST_2:
                e = Expression.constant(2, Integer.TYPE);
                break;
            case ClassFile.ICONST_3:
                e = Expression.constant(3, Integer.TYPE);
                break;
            case ClassFile.ICONST_4:
                e = Expression.constant(4, Integer.TYPE);
                break;
            case ClassFile.ICONST_5:
                e = Expression.constant(5, Integer.TYPE);
                break;
            case ClassFile.LCONST_0:
                e = Expression.constant(0l, Long.TYPE);
                break;
            case ClassFile.LCONST_1:
                e = Expression.constant(1l, Long.TYPE);
                break;
            case ClassFile.IADD:
            case ClassFile.LADD:
            case ClassFile.FADD:
            case ClassFile.DADD:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.add(second, first);
                break;
            case ClassFile.ISUB:
            case ClassFile.LSUB:
            case ClassFile.FSUB:
            case ClassFile.DSUB:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.subtract(second, first);
                break;
            case ClassFile.IMUL:
            case ClassFile.LMUL:
            case ClassFile.FMUL:
            case ClassFile.DMUL:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.multiply(second, first);
                break;
            case ClassFile.IDIV:
            case ClassFile.LDIV:
            case ClassFile.FDIV:
            case ClassFile.DDIV:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.divide(second, first);
                break;
            case ClassFile.IREM:
            case ClassFile.LREM:
            case ClassFile.FREM:
            case ClassFile.DREM:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.modulo(second, first);
                break;
            case ClassFile.INEG:
            case ClassFile.LNEG:
            case ClassFile.FNEG:
            case ClassFile.DNEG:
                first = _exprStack.pop();
                e = Expression.negate(first);
                break;
            case ClassFile.ISHL:
            case ClassFile.LSHL:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.leftShift(second, first);
                break;
            case ClassFile.ISHR:
            case ClassFile.LSHR:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.rightShift(second, first);
                break;
            case ClassFile.IUSHR:
            case ClassFile.LUSHR:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.rightShift(second, first);
                break;
            case ClassFile.IAND:
            case ClassFile.LAND:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.bitwiseAnd(second, first);
                break;
            case ClassFile.IOR:
            case ClassFile.LOR:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.bitwiseOr(second, first);
                break;
            case ClassFile.IXOR:
            case ClassFile.LXOR:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.exclusiveOr(second, first);
                break;
            case ClassFile.I2B:
            case ClassFile.I2C:
            case ClassFile.I2S:
                first = _exprStack.pop();
                e = Expression.convert(first, NumericTypeLookup2[opcode - ClassFile.I2B]);
                break;
            case ClassFile.I2L:
            case ClassFile.I2F:
            case ClassFile.I2D:
                first = _exprStack.pop();
                e = Expression.convert(first, NumericTypeLookup[opcode - ClassFile.I2L + 1]);
                break;
            case ClassFile.L2I:
            case ClassFile.L2F:
            case ClassFile.L2D:
                int l2l = opcode > ClassFile.L2I ? 1 : 0;
                first = _exprStack.pop();
                e = Expression.convert(first, NumericTypeLookup[opcode - ClassFile.L2I + l2l]);
                break;
            case ClassFile.F2I:
            case ClassFile.F2L:
            case ClassFile.F2D:
                int f2f = opcode == ClassFile.F2D ? 1 : 0;
                first = _exprStack.pop();
                e = Expression.convert(first, NumericTypeLookup[opcode - ClassFile.F2I + f2f]);
                break;
            case ClassFile.D2I:
            case ClassFile.D2L:
            case ClassFile.D2F:
                first = _exprStack.pop();
                e = Expression.convert(first, NumericTypeLookup[opcode - ClassFile.D2I]);
                break;
            case ClassFile.IRETURN:
            case ClassFile.LRETURN:
            case ClassFile.FRETURN:
            case ClassFile.DRETURN:
            case ClassFile.ARETURN:

                go(null);

                return;
            case ClassFile.SWAP:
                first = _exprStack.pop();
                second = _exprStack.pop();
                _exprStack.push(first);
                _exprStack.push(second);
            case ClassFile.DUP:
            case ClassFile.DUP_X1:
            case ClassFile.DUP_X2:
            case ClassFile.DUP2:
            case ClassFile.DUP2_X1:
            case ClassFile.DUP2_X2:
                // our stack is not divided to words
                int base = (opcode - ClassFile.DUP) % 3;
                base++;
                dup(_exprStack, base, base - 1);
                return;
            case ClassFile.NOP:
            case ClassFile.RETURN:
                return;
            case ClassFile.POP:
            case ClassFile.POP2:
                if (_statements == null)
                    _statements = new ArrayList<>();
                _statements.add(_exprStack.pop());
                return;
            case ClassFile.AASTORE:
            case ClassFile.BASTORE:
            case ClassFile.CASTORE:
            case ClassFile.DASTORE:
            case ClassFile.FASTORE:
            case ClassFile.IASTORE:
            case ClassFile.LASTORE:
            case ClassFile.SASTORE:
                Expression value = _exprStack.pop();
                Expression index = _exprStack.pop();
                Expression newArrayInit = _exprStack.pop();
                if (!(index instanceof ConstantExpression) || !index.getResultType().equals(Integer.TYPE))
                    throw notLambda(opcode);

                if (!(newArrayInit instanceof NewArrayInitExpression))
                    throw notLambda(opcode);
                NewArrayInitExpression newArrayInitExpression = (NewArrayInitExpression) newArrayInit;
                newArrayInitExpression.getInitializers().set((Integer) ((ConstantExpression) index).getValue(), value);
                return;
            default:
                throw notLambda(opcode);
        }

        _exprStack.push(e);
    }

    private static void dup(ExpressionStack stack,
                            int fromIndex,
                            final int toIndex) {
        if (fromIndex == toIndex)
            return;

        Expression e = stack.get(stack.size() - fromIndex--);
        dup(stack, fromIndex, toIndex);
        stack.push(e);
    }

    //@Override
    public void visitIntInsn(int opcode,
                             int operand) {
        switch (opcode) {
            case ClassFile.BIPUSH:
            case ClassFile.SIPUSH:
                _exprStack.push(Expression.constant(operand, Integer.TYPE));
                break;
            case ClassFile.NEWARRAY:
                _exprStack.push(createNewArrayInitExpression(opcode, arrayTypesByCode[operand - TypeKind.BooleanType.newarrayCode()]));
                break;
            default:
                throw notLambda(opcode);
        }
    }

    //@Override
    public void visitJumpInsn(int opcode,
                              Label label) {
        int etype;
        switch (opcode) {
            case ClassFile.GOTO:

                go(label);

                return;
            default:
            case ClassFile.JSR:
                throw notLambda(opcode);
            case ClassFile.IFEQ:
                etype = ExpressionType.NotEqual; // Equal
                pushZeroConstantOrReduce();
                break;
            case ClassFile.IFNE:
                etype = ExpressionType.Equal; // NotEqual
                pushZeroConstantOrReduce();
                break;
            case ClassFile.IFLT:
                etype = ExpressionType.GreaterThanOrEqual; // LessThan
                pushZeroConstantOrReduce();
                break;
            case ClassFile.IFGE:
                etype = ExpressionType.LessThan; // GreaterThanOrEqual
                pushZeroConstantOrReduce();
                break;
            case ClassFile.IFGT:
                etype = ExpressionType.LessThanOrEqual; // GreaterThan
                pushZeroConstantOrReduce();
                break;
            case ClassFile.IFLE:
                etype = ExpressionType.GreaterThan; // LessThanOrEqual
                pushZeroConstantOrReduce();
                break;
            case ClassFile.IF_ICMPEQ:
            case ClassFile.IF_ACMPEQ: // ??
                etype = ExpressionType.NotEqual; // Equal
                break;
            case ClassFile.IF_ICMPNE:
            case ClassFile.IF_ACMPNE: // ??
                etype = ExpressionType.Equal; // NotEqual
                break;
            case ClassFile.IF_ICMPLT:
                etype = ExpressionType.GreaterThanOrEqual; // LessThan
                break;
            case ClassFile.IF_ICMPGE:
                etype = ExpressionType.LessThan; // GreaterThanOrEqual
                break;
            case ClassFile.IF_ICMPGT:
                etype = ExpressionType.LessThanOrEqual; // GreaterThan
                break;
            case ClassFile.IF_ICMPLE:
                etype = ExpressionType.GreaterThan; // LessThanOrEqual
                break;
            case ClassFile.IFNULL:
            case ClassFile.IFNONNULL:
                Expression e = Expression.isNull(_exprStack.pop());
                if (opcode == ClassFile.IFNULL) // IFNONNULL
                    e = Expression.logicalNot(e);

                branch(label, e);

                return;
        }

        Expression second = _exprStack.pop();
        Expression first = _exprStack.pop();
        Expression e = Expression.binary(etype, first, second);

        branch(label, e);
    }

    private static ExpressionStack reduce(ExpressionStack first,
                                          ExpressionStack second) {

        int fDepth = first.getDepth();
        int sDepth = second.getDepth();

        if (fDepth == sDepth) {
            ExpressionStack.BranchExpression firstB = first.getParent();
            ExpressionStack.BranchExpression secondB = second.getParent();

            if (firstB == secondB) {

                ExpressionStack parentStack = firstB.getParent();
                parentStack.pop(); // branch

                Expression right = firstB.getTrue().pop();
                Expression left = firstB.getFalse().pop();
                assert normalizePrimitive(right.getResultType()) == normalizePrimitive(
                        left.getResultType()) : "branches must evaluate to same type";
                parentStack.push(Expression.condition(firstB.getTest(), right, left));

                return parentStack;
            } else if (first.size() == 0 && second.size() == 0) {

                ExpressionStack.BranchExpression firstBB = firstB.getParent().getParent();
                ExpressionStack.BranchExpression secondBB = secondB.getParent().getParent();

                if (firstBB == secondBB) {

                    ExpressionStack l;

                    Expression fTest = firstB.getTest();
                    if (firstB.getTrue() != first) {
                        fTest = Expression.logicalNot(fTest);
                        l = firstB.getTrue();
                    } else
                        l = firstB.getFalse();

                    Expression sTest = secondB.getTest();
                    if (secondB.getTrue() != second) {
                        sTest = Expression.logicalNot(sTest);
                        secondB.getTrue().reduce();
                    } else
                        secondB.getFalse().reduce();

                    Expression rootTest = firstBB.getTest();
                    if (firstBB.getTrue() != firstB.getParent())
                        rootTest = Expression.logicalNot(rootTest);

                    rootTest = Expression.condition(rootTest, fTest, sTest);

                    ExpressionStack parentStack = firstBB.getParent();

                    ExpressionStack.BranchExpression be = new ExpressionStack.BranchExpression(parentStack, rootTest,
                            first, l);

                    parentStack.pop(); // old branch

                    parentStack.add(be);

                    return first;
                }
            }
        } else if (first.size() == 0 && second.size() == 0) {
            ExpressionStack older;
            ExpressionStack younger;

            if (fDepth > sDepth) {
                older = second;
                younger = first;
            } else {
                older = first;
                younger = second;
            }

            final boolean trueB = older.getParent().getTrue() == older;

            BranchExpression youngerBranch = younger.getParent();
            Expression youngTest = youngerBranch.getTest();

            ExpressionStack other;
            if (younger.getParent().get(trueB) != younger) {
                youngTest = Expression.logicalNot(youngTest);
                other = youngerBranch.get(trueB);
            } else
                other = youngerBranch.get(!trueB);

            Expression test = Expression.logicalAnd(older.getParent().getTest(), youngTest);

            if (!trueB)
                test = Expression.logicalNot(test);

            ExpressionStack parentStack = older.getParent().getParent();

            ExpressionStack.BranchExpression be = new ExpressionStack.BranchExpression(parentStack, test, older, other);

            parentStack.pop(); // old branch

            parentStack.add(be);

            return older;
        }

        return null;
    }

    private static ExpressionStack reduce(List<ExpressionStack> bl) {
        int index = bl.size() - 1;
        ExpressionStack second = bl.remove(index--);
        if (index < 0)
            return second;

        ExpressionStack first = bl.get(index);
        ExpressionStack reduced = reduce(first, second);
        if (reduced != null) {
            bl.set(index, reduced);
            return reduce(bl);
        }

        first = reduce(bl);

        return reduce(first, second);
    }

    //@Override
    public void visitLabel(Label label) {
        List<ExpressionStack> bl = _branches.remove(label);
        if (bl == null)
            return;

        for (int i = bl.size() - 1; i >= 0; i--) {
            ExpressionStack es = bl.get(i);
            if (es.isReduced())
                bl.remove(i);
        }

        if (_exprStack != null)
            bl.add(_exprStack);

        _exprStack = reduce(bl);
        assert _exprStack != null;
    }

    //@Override
    public void visitLdcInsn(ConstantDesc cst) {
        Class<?> type = _primitives.get(cst.getClass());
        if (type == null) {
            if (cst instanceof String) {
                type = String.class;
            } else if (cst instanceof ClassDesc cd) {
                type = _classVisitor.getClass(Signature.of(cd));
            }
        }
        _exprStack.push(Expression.constant(cst, type));
    }

    //@Override
    public void visitLineNumber(int line,
                                Label start) {

    }

    //@Override
    public void visitLocalVariable(String name,
                                   String desc,
                                   String signature,
                                   Label start,
                                   Label end,
                                   int index) {
        throw notLambda(-1);
    }

    //@Override
    public void visitLookupSwitchInsn(Label dflt,
                                      int[] keys,
                                      Label[] labels) {
        throw notLambda(ClassFile.LOOKUPSWITCH);
    }

    //@Override
    public void visitMaxs(int maxStack,
                          int maxLocals) {
        if (_localVariables != null) {
            if (_me != null)
                maxLocals--;

            maxLocals = compensate2SlotsValues(maxLocals);
            maxLocals -= _argTypes.length;
            _localVariables = Arrays.copyOf(_localVariables, maxLocals);
        }
    }

    static final char TAG_ARG = '\u0001';
    static final char TAG_CONST = '\u0002';

    private void makeConcatWithConstants(MethodSignature sig, List<ConstantDesc> bootstrapMethodArguments) throws NoSuchMethodException {

        Class<?>[] parameterTypes = getParameterTypes(sig.arguments());
        Expression[] params = new Expression[parameterTypes.length];
        for (var i = params.length - 1; i >= 0; i--)
            params[i] = _exprStack.pop();
        var recipe = (String) bootstrapMethodArguments.get(0);

        Expression esb = Expression.newInstance(StringBuilder.class.getConstructor(), List.of());
        var appendString = StringBuilder.class.getMethod("append", String.class);

        var curConst = 1;
        var curParam = 0;
        var b = new StringBuilder();
        for (var i = 0; i < recipe.length(); i++) {
            var c = recipe.charAt(i);

            if (c == TAG_ARG) {
                if (b.length() > 0) {
                    esb = Expression.invoke(esb, appendString, Expression.constant(b.toString()));
                    b.setLength(0);
                }

                var append = getAppendMethod(parameterTypes[curParam]);
                esb = Expression.invoke(esb, append, params[curParam++]);
                continue;
            }

            if (c == TAG_CONST) b.append(bootstrapMethodArguments.get(curConst++));
            else b.append(c);
        }

        if (b.length() > 0) {
            esb = Expression.invoke(esb, appendString, Expression.constant(b.toString()));
        }

        var result = Expression.invoke(esb, StringBuilder.class.getMethod("toString"));
        _exprStack.push(result);
    }

    private static Method getAppendMethod(Class<?> type) throws NoSuchMethodException {
        Method append = null;

        if (!type.isPrimitive()) {
            try {
                append = StringBuilder.class.getMethod("append", type);
            } catch (NoSuchMethodException no) {
                type = Object.class;
            }
        } else if (type == Byte.TYPE || type == Short.TYPE) {
            type = Integer.TYPE;
        }

        if (append == null)
            append = StringBuilder.class.getMethod("append", type);
        return append;
    }

    //@Override
    public void visitInvokeDynamicInsn(String name,
                                       MethodTypeDesc descriptor,
                                       DirectMethodHandleDesc bootstrapMethodHandle,
                                       List<ConstantDesc> bootstrapMethodArguments) {

        var sig = MethodSignature.parseFrom(descriptor.descriptorString());
        String bootMethod = bootstrapMethodHandle.methodName();
        if (bootstrapMethodHandle.owner().descriptorString().equals("Ljava/lang/invoke/StringConcatFactory;") && name.equals("makeConcatWithConstants")) {
            try {
                makeConcatWithConstants(sig, bootstrapMethodArguments);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            return;
        }
        if (!bootstrapMethodHandle.owner().descriptorString().equals(LambdaMetafactoryClassInternalName)
                || !"Metafactory".regionMatches(true, 0, bootMethod, bootMethod.length() - "Metafactory".length(),
                "Metafactory".length())) {
            throw new UnsupportedOperationException("Unsupported bootstrapMethodHandle: " + bootstrapMethodHandle);
        }

        var handle = (DirectMethodHandleDesc) bootstrapMethodArguments.get(1);
        var internalName = handle.owner().descriptorString();
        var objectType = Signature.parseFrom(internalName);
        var containingClass = _classVisitor.getClass(objectType);

        var hasThis = handle.kind().isInterface || handle.kind() == DirectMethodHandleDesc.Kind.SPECIAL
                || handle.kind() == DirectMethodHandleDesc.Kind.VIRTUAL;

        Expression optionalThis = hasThis ? Expression.parameter(containingClass, 0) : null;
        var methodDescriptor = handle.lookupDescriptor();
        var methodSig = MethodSignature.parseFrom(methodDescriptor);
        var targetParameterTypes = getParameterTypes(methodSig.arguments());
        var methodName = handle.methodName();
        Method method;
        try {
            method = containingClass.getDeclaredMethod(methodName, targetParameterTypes);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        var params = Expression.getParameters(method);
        var member = Expression.member(ExpressionType.MethodAccess, optionalThis, method,
                method.getReturnType(), params);

           /* if (!hasThis && argsTypes.length == 0) {
                _exprStack.push(member);
                return;
            }*/

        if (hasThis) {
            params = new ArrayList<>(targetParameterTypes.length);
            for (int i = 0; i < targetParameterTypes.length; i++) {
                params.add(Expression.parameter(targetParameterTypes[i], i + 1));
            }
        }

        var call = Expression.invoke(member, params);
        if (hasThis) {
            params.add(0, (ParameterExpression) optionalThis);
        }
        var methodLoader = _classVisitor.getLoader();
        var lambda = Expression.lambda(call.getResultType(), call, params, Collections.emptyList(), null,
                method.isSynthetic() ? () -> ExpressionClassCracker.get()
                        .lambdaFromClassLoader(methodLoader, containingClass.getName(), optionalThis,
                                methodName,
                                methodDescriptor) : null);

        var argsTypes = sig.arguments();
        if (argsTypes.isEmpty()) {
            _exprStack.push(lambda);
            return;
        }

        var arguments = createArguments(sig.arguments());

        Class<?>[] parameterTypes = getParameterTypes(sig.arguments());
        convertArguments(arguments, parameterTypes);
        params = new ArrayList<>(parameterTypes.length);
        for (int i = 0; i < parameterTypes.length; i++) {
            params.add(Expression.parameter(parameterTypes[i], i));
        }
        LambdaExpression<?> partial = Expression.lambda(lambda.getResultType(), lambda, params,
                Collections.emptyList(), null);
        InvocationExpression e = Expression.invoke(partial, arguments);

        _exprStack.push(e);
    }

    //@Override
    public void visitMethodInsn(int opcode,
                                ClassEntry owner,
                                String name,
                                MethodTypeDesc desc,
                                boolean itf) {

        var sig = MethodSignature.parseFrom(desc.descriptorString());
        var argsTypes = sig.arguments();

        // Class<?>[] parameterTypes = getParameterTypes(argsTypes);

        Expression[] arguments = createArguments(argsTypes);

        Expression e;

        switch (opcode) {
            case ClassFile.INVOKESPECIAL:
                if (name.equals("<init>")) {
                    Class<?>[] parameterTypes = getParameterTypes(argsTypes);
                    convertArguments(arguments, parameterTypes);
                    try {
                        e = Expression.newInstance(_exprStack.pop().getResultType(), parameterTypes, arguments);
                    } catch (NoSuchMethodException nsme) {
                        throw new RuntimeException(nsme);
                    }
                    _exprStack.pop(); // going to re-add it, which is not the JVM
                    // semantics
                    break;
                }
            case ClassFile.INVOKEVIRTUAL:
            case ClassFile.INVOKEINTERFACE:
                try {
                    Class<?> lambdaClass = _classVisitor.getClass(Signature.ClassTypeSig.of(owner.asInternalName()));
                    Expression instance = _exprStack.pop();
                    if (instance.getExpressionType() == ExpressionType.Constant) {
                        Object value = ((ConstantExpression) instance).getValue();
                        if (value instanceof SerializedLambda) {
                            SerializedLambda serialized = (SerializedLambda) value;
                            ClassLoader lambdaClassLoader = _classVisitor.getLoader();
                            Class<?> serializedClass;
                            try {
                                serializedClass = lambdaClassLoader
                                        .loadClass(serialized.getFunctionalInterfaceClass().replace('/', '.'));
                            } catch (ClassNotFoundException cnfe) {
                                throw new RuntimeException(cnfe);
                            }

                            if (!lambdaClass.isAssignableFrom(serializedClass))
                                throw new ClassCastException(serializedClass + " cannot be cast to " + lambdaClass);

                            if (!serialized.getFunctionalInterfaceMethodName().equals(name))
                                throw new NoSuchMethodException(name);

                            LambdaExpression<?> lambda = ExpressionClassCracker.get().lambda(serialized, lambdaClassLoader);
                            Class<?>[] parameterTypes = lambda.getParameters()
                                    .stream()
                                    .map(ParameterExpression::getResultType)
                                    .toArray(Class[]::new);
                            convertArguments(arguments, parameterTypes);
                            e = Expression.invoke(lambda, arguments);
                            break;
                        } else {
                            Class<? extends Object> instanceClass = value.getClass();

                            if (instanceClass.isSynthetic()) {
                                LambdaExpression<?> inst = (value instanceof Serializable)
                                        ? ExpressionClassCracker.get().lambda(value, true)
                                        : ExpressionClassCracker.get()
                                        .lambdaFromFileSystem(value,
                                                instance.getResultType()
                                                        .getDeclaredMethod(name, getParameterTypes(argsTypes)),
                                                null);

                                e = Expression.invoke(inst, arguments);

                                break;
                            }
                        }
                    }

                    Class<?>[] parameterTypes = getParameterTypes(argsTypes);
                    convertArguments(arguments, parameterTypes);
                    e = Expression.invoke(TypeConverter.convert(instance, lambdaClass), name, parameterTypes, arguments);

                } catch (NoSuchMethodException nsme) {
                    throw new RuntimeException(nsme);
                }
                break;

            case ClassFile.INVOKESTATIC:
                Class<?>[] parameterTypes = getParameterTypes(argsTypes);
                convertArguments(arguments, parameterTypes);
                try {
                    Class<?> targetType = _classVisitor.getClass(Signature.ClassTypeSig.of(owner.asInternalName()));
                    if (targetType.isSynthetic()) {
                        LambdaExpression<?> lambda = ExpressionClassCracker.get()
                                .lambdaFromFileSystem(null,
                                        targetType.getDeclaredMethod(name, getParameterTypes(argsTypes)),
                                        this._classVisitor.getLoader());
                        e = Expression.invoke(lambda, arguments);
                    } else {
                        e = Expression.invoke(targetType, name, parameterTypes, arguments);
                    }
                } catch (NoSuchMethodException nsme) {
                    throw new RuntimeException(nsme);
                }
                break;

            default:
                throw new IllegalArgumentException("opcode: " + opcode);
        }

        _exprStack.push(e);
    }

    private void convertArguments(Expression[] arguments,
                                  Class<?>[] parameterTypes) {

        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = TypeConverter.convert(arguments[i], parameterTypes[i]);
        }
    }

    private Expression[] createArguments(List<Signature> argsTypes) {
        Expression[] arguments = new Expression[argsTypes.size()];
        for (int i = argsTypes.size(); i > 0; ) {
            i--;
            arguments[i] = _exprStack.pop();
        }
        return arguments;
    }

    private Class<?>[] getParameterTypes(List<Signature> argsTypes) {
        Class<?>[] parameterTypes = new Class<?>[argsTypes.size()];
        for (int i = 0; i < argsTypes.size(); i++)
            parameterTypes[i] = _classVisitor.getClass(argsTypes.get(i));
        return parameterTypes;
    }

    // //@Overrides
    //@Override
    public void visitMultiANewArrayInsn(ClassEntry desc,
                                        int dims) {
        throw notLambda(ClassFile.MULTIANEWARRAY);
    }

   /* //@Override
    public AnnotationVisitor visitParameterAnnotation(int arg0,
                                                      String arg1,
                                                      boolean arg2) {
        return null;
    }*/

    //@Override
    public void visitTableSwitchInsn(int min,
                                     int max,
                                     Label dflt,
                                     List<SwitchCase> cases) {
        throw notLambda(ClassFile.TABLESWITCH);
    }

    //@Override
    public void visitTryCatchBlock(Label start,
                                   Label end,
                                   Label handler,
                                   Optional<ClassEntry> catchType) {
        throw notLambda(-2);
    }

    //@Override
    public void visitTypeInsn(int opcode,
                              ClassEntry type) {
        Class<?> resultType = _classVisitor.getClass(Signature.ClassTypeSig.of(type.asInternalName()));
        Expression e;
        switch (opcode) {
            case ClassFile.NEW:
                e = Expression.constant(null, resultType);
                break;
            case ClassFile.CHECKCAST:
                if (resultType == Object.class)
                    // there is no point in casting to object
                    return;
                e = Expression.convert(_exprStack.pop(), resultType);
                break;
            case ClassFile.ANEWARRAY:
                e = createNewArrayInitExpression(opcode, resultType);
                break;

            case ClassFile.INSTANCEOF:
                e = Expression.instanceOf(_exprStack.pop(), resultType);
                break;
            default:
                throw notLambda(opcode);
        }

        _exprStack.push(e);
    }

    private Expression createNewArrayInitExpression(int opcode,
                                                    Class<?> componentType) {
        Expression count = _exprStack.pop();
        if (!(count instanceof ConstantExpression) || !count.getResultType().equals(Integer.TYPE))
            throw notLambda(opcode);

        return new NewArrayInitExpression(componentType, (Integer) ((ConstantExpression) count).getValue());
    }

    //@Override
    public void visitVarInsn(int opcode,
                             int var) {
        if (_me != null) {
            if (var == 0) {
                _exprStack.push(_me);
                return;
            }
            var--;
        }

        var = compensate2SlotsValues(var);

        Class<?> type;
        switch (opcode) {
            case ClassFile.ISTORE:
            case ClassFile.ISTORE_0:
            case ClassFile.ISTORE_1:
            case ClassFile.ISTORE_2:
            case ClassFile.ISTORE_3:
            case ClassFile.LSTORE:
            case ClassFile.LSTORE_0:
            case ClassFile.LSTORE_1:
            case ClassFile.LSTORE_2:
            case ClassFile.LSTORE_3:
            case ClassFile.FSTORE:
            case ClassFile.FSTORE_0:
            case ClassFile.FSTORE_1:
            case ClassFile.FSTORE_2:
            case ClassFile.FSTORE_3:
            case ClassFile.DSTORE:
            case ClassFile.DSTORE_0:
            case ClassFile.DSTORE_1:
            case ClassFile.DSTORE_2:
            case ClassFile.DSTORE_3:
            case ClassFile.ASTORE:
            case ClassFile.ASTORE_0:
            case ClassFile.ASTORE_1:
            case ClassFile.ASTORE_2:
            case ClassFile.ASTORE_3:
                if (_localVariables == null)
                    _localVariables = new Expression[10];

                var -= _argTypes.length;

                if (var < 0)
                    throw new IllegalArgumentException("Parameter cannot be reassigned. Use local variables.");
                else if (var >= _localVariables.length)
                    _localVariables = Arrays.copyOf(_localVariables, var >> 1);
                else if (_localVariables[var] != null)
                    throw new IllegalArgumentException("Local variable must be final or effectively final.");

                _localVariables[var] = _exprStack.pop();
                return;
            case ClassFile.RET:
            default:
                throw notLambda(opcode);
            case ClassFile.ILOAD_0:
            case ClassFile.ILOAD_1:
            case ClassFile.ILOAD_2:
            case ClassFile.ILOAD_3:
            case ClassFile.ILOAD:
                type = Integer.TYPE;
                break;
            case ClassFile.LLOAD_0:
            case ClassFile.LLOAD_1:
            case ClassFile.LLOAD_2:
            case ClassFile.LLOAD_3:
            case ClassFile.LLOAD:
                type = Long.TYPE;
                break;
            case ClassFile.FLOAD_0:
            case ClassFile.FLOAD_1:
            case ClassFile.FLOAD_2:
            case ClassFile.FLOAD_3:
            case ClassFile.FLOAD:
                type = Float.TYPE;
                break;
            case ClassFile.DLOAD_0:
            case ClassFile.DLOAD_1:
            case ClassFile.DLOAD_2:
            case ClassFile.DLOAD_3:
            case ClassFile.DLOAD:
                type = Double.TYPE;
                break;
            case ClassFile.ALOAD_0:
            case ClassFile.ALOAD_1:
            case ClassFile.ALOAD_2:
            case ClassFile.ALOAD_3:
            case ClassFile.ALOAD:
                if (var < _argTypes.length)
                    type = _argTypes[var];
                else {
                    int localVar = var - _argTypes.length;
                    type = _localVariables[localVar].getResultType();
                }
                break;
        }

        _exprStack.push(Expression.parameter(type, var));
    }

    private int compensate2SlotsValues(int var) {
        // 64 bit values hold 2 slots on the stack - compensate it
        for (int i = 0; i < var && i < _argTypes.length; i++) {
            Class<?> clazz = _argTypes[i];
            if (clazz == Long.TYPE || clazz == Double.TYPE)
                var--;
        }
        return var;
    }

    static RuntimeException notLambda(int opcode) {
        String opcodeName = Integer.toString(opcode);
        Field[] ops = ClassFile.class.getFields();
        for (int i = 0; i < ops.length; i++) {
            Field f = ops[i];
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == Integer.TYPE) {
                try {
                    int test = f.getInt(null);
                    if (test == opcode) {
                        opcodeName = f.getName();
                        break;
                    }
                } catch (IllegalAccessException e) {
                    // suppress;
                    break;
                }
            }
        }
        return new IllegalArgumentException("Not a lambda expression. Opcode " + opcodeName + " is illegal.");
    }

}
