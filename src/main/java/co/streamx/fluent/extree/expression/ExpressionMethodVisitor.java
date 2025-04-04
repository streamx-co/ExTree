package co.streamx.fluent.extree.expression;

import java.io.Serializable;
import io.github.dmlloyd.classfile.*;
import io.github.dmlloyd.classfile.constantpool.ClassEntry;
//import java.lang.classfile.constantpool.LoadableConstantEntry;
import io.github.dmlloyd.classfile.instruction.SwitchCase;
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
    public void visitFieldInsn(Opcode opcode,
                               ClassEntry owner,
                               String name,
                               String desc) {
        Expression e;
        boolean isSyntheticConstant = false;
        switch (opcode) {
            case Opcode.GETFIELD:
                Expression instance = _exprStack.pop();
                try {
                    e = Expression.get(instance, name);
                } catch (NoSuchFieldException nsfe) {
                    throw new RuntimeException(nsfe);
                }
                if (instance.getExpressionType() == ExpressionType.Constant && instance.getResultType().isSynthetic())
                    isSyntheticConstant = true;
                break;
            case Opcode.GETSTATIC:
                try {
                    Class<?> containingClass = _classVisitor.getClass(Signature.ClassTypeSig.of(owner.asInternalName()));
                    e = Expression.get(containingClass, name);
                    if (containingClass.isSynthetic())
                        isSyntheticConstant = true;
                } catch (NoSuchFieldException nsfe) {
                    throw new RuntimeException(nsfe);
                }
                break;
            case Opcode.PUTFIELD:
            case Opcode.PUTSTATIC:
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
    public void visitIincInsn(int arg0,
                              int arg1) {
        throw notLambda(Opcode.IINC);
    }

    //@Override
    public void visitInsn(Opcode opcode) {
        Expression e;
        Expression first;
        Expression second;
        switch (opcode) {
            case Opcode.ARRAYLENGTH:
                e = Expression.arrayLength(_exprStack.pop());
                break;
            case Opcode.ACONST_NULL:
                e = Expression.constant(null, Object.class);
                break;
            case Opcode.IALOAD:
            case Opcode.LALOAD:
            case Opcode.FALOAD:
            case Opcode.DALOAD:
            case Opcode.AALOAD:
            case Opcode.BALOAD:
            case Opcode.CALOAD:
            case Opcode.SALOAD:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.arrayIndex(second, first);
                break;
            case Opcode.DCONST_0:
                e = Expression.constant(0d, Double.TYPE);
                break;
            case Opcode.DCONST_1:
                e = Expression.constant(1d, Double.TYPE);
                break;
            case Opcode.FCMPG:
            case Opcode.FCMPL:
            case Opcode.DCMPG:
            case Opcode.DCMPL:
            case Opcode.LCMP:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.subtract(second, first);
                break;
            case Opcode.FCONST_0:
                e = Expression.constant(0f, Float.TYPE);
                break;
            case Opcode.FCONST_1:
                e = Expression.constant(1f, Float.TYPE);
                break;
            case Opcode.FCONST_2:
                e = Expression.constant(2f, Float.TYPE);
                break;
            case Opcode.ICONST_M1:
                e = Expression.constant(-1, Integer.TYPE);
                break;
            case Opcode.ICONST_0:
                e = Expression.constant(0, Integer.TYPE);
                break;
            case Opcode.ICONST_1:
                e = Expression.constant(1, Integer.TYPE);
                break;
            case Opcode.ICONST_2:
                e = Expression.constant(2, Integer.TYPE);
                break;
            case Opcode.ICONST_3:
                e = Expression.constant(3, Integer.TYPE);
                break;
            case Opcode.ICONST_4:
                e = Expression.constant(4, Integer.TYPE);
                break;
            case Opcode.ICONST_5:
                e = Expression.constant(5, Integer.TYPE);
                break;
            case Opcode.LCONST_0:
                e = Expression.constant(0l, Long.TYPE);
                break;
            case Opcode.LCONST_1:
                e = Expression.constant(1l, Long.TYPE);
                break;
            case Opcode.IADD:
            case Opcode.LADD:
            case Opcode.FADD:
            case Opcode.DADD:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.add(second, first);
                break;
            case Opcode.ISUB:
            case Opcode.LSUB:
            case Opcode.FSUB:
            case Opcode.DSUB:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.subtract(second, first);
                break;
            case Opcode.IMUL:
            case Opcode.LMUL:
            case Opcode.FMUL:
            case Opcode.DMUL:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.multiply(second, first);
                break;
            case Opcode.IDIV:
            case Opcode.LDIV:
            case Opcode.FDIV:
            case Opcode.DDIV:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.divide(second, first);
                break;
            case Opcode.IREM:
            case Opcode.LREM:
            case Opcode.FREM:
            case Opcode.DREM:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.modulo(second, first);
                break;
            case Opcode.INEG:
            case Opcode.LNEG:
            case Opcode.FNEG:
            case Opcode.DNEG:
                first = _exprStack.pop();
                e = Expression.negate(first);
                break;
            case Opcode.ISHL:
            case Opcode.LSHL:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.leftShift(second, first);
                break;
            case Opcode.ISHR:
            case Opcode.LSHR:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.rightShift(second, first);
                break;
            case Opcode.IUSHR:
            case Opcode.LUSHR:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.rightShift(second, first);
                break;
            case Opcode.IAND:
            case Opcode.LAND:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.bitwiseAnd(second, first);
                break;
            case Opcode.IOR:
            case Opcode.LOR:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.bitwiseOr(second, first);
                break;
            case Opcode.IXOR:
            case Opcode.LXOR:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.exclusiveOr(second, first);
                break;
            case Opcode.I2B:
            case Opcode.I2C:
            case Opcode.I2S:
                first = _exprStack.pop();
                e = Expression.convert(first, NumericTypeLookup2[opcode.bytecode() - Opcode.I2B.bytecode()]);
                break;
            case Opcode.I2L:
            case Opcode.I2F:
            case Opcode.I2D:
                first = _exprStack.pop();
                e = Expression.convert(first, NumericTypeLookup[opcode.bytecode() - Opcode.I2L.bytecode() + 1]);
                break;
            case Opcode.L2I:
            case Opcode.L2F:
            case Opcode.L2D:
                int l2l = opcode.bytecode() > Opcode.L2I.bytecode() ? 1 : 0;
                first = _exprStack.pop();
                e = Expression.convert(first, NumericTypeLookup[opcode.bytecode() - Opcode.L2I.bytecode() + l2l]);
                break;
            case Opcode.F2I:
            case Opcode.F2L:
            case Opcode.F2D:
                int f2f = opcode == Opcode.F2D ? 1 : 0;
                first = _exprStack.pop();
                e = Expression.convert(first, NumericTypeLookup[opcode.bytecode() - Opcode.F2I.bytecode() + f2f]);
                break;
            case Opcode.D2I:
            case Opcode.D2L:
            case Opcode.D2F:
                first = _exprStack.pop();
                e = Expression.convert(first, NumericTypeLookup[opcode.bytecode() - Opcode.D2I.bytecode()]);
                break;
            case Opcode.IRETURN:
            case Opcode.LRETURN:
            case Opcode.FRETURN:
            case Opcode.DRETURN:
            case Opcode.ARETURN:

                go(null);

                return;
            case Opcode.SWAP:
                first = _exprStack.pop();
                second = _exprStack.pop();
                _exprStack.push(first);
                _exprStack.push(second);
            case Opcode.DUP:
            case Opcode.DUP_X1:
            case Opcode.DUP_X2:
            case Opcode.DUP2:
            case Opcode.DUP2_X1:
            case Opcode.DUP2_X2:
                // our stack is not divided to words
                int base = (opcode.bytecode() - Opcode.DUP.bytecode()) % 3;
                base++;
                dup(_exprStack, base, base - 1);
                return;
            case Opcode.NOP:
            case Opcode.RETURN:
                return;
            case Opcode.POP:
            case Opcode.POP2:
                if (_statements == null)
                    _statements = new ArrayList<>();
                _statements.add(_exprStack.pop());
                return;
            case Opcode.AASTORE:
            case Opcode.BASTORE:
            case Opcode.CASTORE:
            case Opcode.DASTORE:
            case Opcode.FASTORE:
            case Opcode.IASTORE:
            case Opcode.LASTORE:
            case Opcode.SASTORE:
                Expression value = _exprStack.pop();
                Expression index = _exprStack.pop();
                Expression newArrayInit = _exprStack.pop();
                if (!(index instanceof ConstantExpression) || !index.getResultType().equals(Integer.TYPE))
                    throw notLambda(opcode);

                if (!(newArrayInit instanceof NewArrayInitExpression newArrayInitExpression))
                    throw notLambda(opcode);
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
    public void visitIntInsn(Opcode opcode,
                             int operand) {
        switch (opcode) {
            case Opcode.BIPUSH:
            case Opcode.SIPUSH:
                _exprStack.push(Expression.constant(operand, Integer.TYPE));
                break;
            case Opcode.NEWARRAY:
                _exprStack.push(createNewArrayInitExpression(opcode, arrayTypesByCode[operand - TypeKind.BOOLEAN.newarrayCode()]));
                break;
            default:
                throw notLambda(opcode);
        }
    }

    //@Override
    public void visitJumpInsn(Opcode opcode,
                              Label label) {
        int etype;
        switch (opcode) {
            case Opcode.GOTO:

                go(label);

                return;
            default:
            case Opcode.JSR:
                throw notLambda(opcode);
            case Opcode.IFEQ:
                etype = ExpressionType.NotEqual; // Equal
                pushZeroConstantOrReduce();
                break;
            case Opcode.IFNE:
                etype = ExpressionType.Equal; // NotEqual
                pushZeroConstantOrReduce();
                break;
            case Opcode.IFLT:
                etype = ExpressionType.GreaterThanOrEqual; // LessThan
                pushZeroConstantOrReduce();
                break;
            case Opcode.IFGE:
                etype = ExpressionType.LessThan; // GreaterThanOrEqual
                pushZeroConstantOrReduce();
                break;
            case Opcode.IFGT:
                etype = ExpressionType.LessThanOrEqual; // GreaterThan
                pushZeroConstantOrReduce();
                break;
            case Opcode.IFLE:
                etype = ExpressionType.GreaterThan; // LessThanOrEqual
                pushZeroConstantOrReduce();
                break;
            case Opcode.IF_ICMPEQ:
            case Opcode.IF_ACMPEQ: // ??
                etype = ExpressionType.NotEqual; // Equal
                break;
            case Opcode.IF_ICMPNE:
            case Opcode.IF_ACMPNE: // ??
                etype = ExpressionType.Equal; // NotEqual
                break;
            case Opcode.IF_ICMPLT:
                etype = ExpressionType.GreaterThanOrEqual; // LessThan
                break;
            case Opcode.IF_ICMPGE:
                etype = ExpressionType.LessThan; // GreaterThanOrEqual
                break;
            case Opcode.IF_ICMPGT:
                etype = ExpressionType.LessThanOrEqual; // GreaterThan
                break;
            case Opcode.IF_ICMPLE:
                etype = ExpressionType.GreaterThan; // LessThanOrEqual
                break;
            case Opcode.IFNULL:
            case Opcode.IFNONNULL:
                Expression e = Expression.isNull(_exprStack.pop());
                if (opcode == Opcode.IFNULL) // IFNONNULL
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
    public void visitLookupSwitchInsn(Label dflt,
                                      int[] keys,
                                      Label[] labels) {
        throw notLambda(Opcode.LOOKUPSWITCH);
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
    public void visitMethodInsn(Opcode opcode,
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
            case Opcode.INVOKESPECIAL:
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
            case Opcode.INVOKEVIRTUAL:
            case Opcode.INVOKEINTERFACE:
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

            case Opcode.INVOKESTATIC:
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
        throw notLambda(Opcode.MULTIANEWARRAY);
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
        throw notLambda(Opcode.TABLESWITCH);
    }

    //@Override
    public void visitTryCatchBlock(Label start,
                                   Label end,
                                   Label handler,
                                   Optional<ClassEntry> catchType) {
        throw notLambda("try-catch block");
    }

    //@Override
    public void visitTypeInsn(Opcode opcode,
                              ClassEntry type) {
        Class<?> resultType = _classVisitor.getClass(Signature.ClassTypeSig.of(type.asInternalName()));
        Expression e;
        switch (opcode) {
            case Opcode.NEW:
                e = Expression.constant(null, resultType);
                break;
            case Opcode.CHECKCAST:
                if (resultType == Object.class)
                    // there is no point in casting to object
                    return;
                e = Expression.convert(_exprStack.pop(), resultType);
                break;
            case Opcode.ANEWARRAY:
                e = createNewArrayInitExpression(opcode, resultType);
                break;

            case Opcode.INSTANCEOF:
                e = Expression.instanceOf(_exprStack.pop(), resultType);
                break;
            default:
                throw notLambda(opcode);
        }

        _exprStack.push(e);
    }

    private Expression createNewArrayInitExpression(Opcode opcode,
                                                    Class<?> componentType) {
        Expression count = _exprStack.pop();
        if (!(count instanceof ConstantExpression) || !count.getResultType().equals(Integer.TYPE))
            throw notLambda(opcode);

        return new NewArrayInitExpression(componentType, (Integer) ((ConstantExpression) count).getValue());
    }

    //@Override
    public void visitVarInsn(Opcode opcode,
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
            case Opcode.ISTORE:
            case Opcode.ISTORE_0:
            case Opcode.ISTORE_1:
            case Opcode.ISTORE_2:
            case Opcode.ISTORE_3:
            case Opcode.LSTORE:
            case Opcode.LSTORE_0:
            case Opcode.LSTORE_1:
            case Opcode.LSTORE_2:
            case Opcode.LSTORE_3:
            case Opcode.FSTORE:
            case Opcode.FSTORE_0:
            case Opcode.FSTORE_1:
            case Opcode.FSTORE_2:
            case Opcode.FSTORE_3:
            case Opcode.DSTORE:
            case Opcode.DSTORE_0:
            case Opcode.DSTORE_1:
            case Opcode.DSTORE_2:
            case Opcode.DSTORE_3:
            case Opcode.ASTORE:
            case Opcode.ASTORE_0:
            case Opcode.ASTORE_1:
            case Opcode.ASTORE_2:
            case Opcode.ASTORE_3:
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
            case Opcode.RET:
            default:
                throw notLambda(opcode);
            case Opcode.ILOAD_0:
            case Opcode.ILOAD_1:
            case Opcode.ILOAD_2:
            case Opcode.ILOAD_3:
            case Opcode.ILOAD:
                type = Integer.TYPE;
                break;
            case Opcode.LLOAD_0:
            case Opcode.LLOAD_1:
            case Opcode.LLOAD_2:
            case Opcode.LLOAD_3:
            case Opcode.LLOAD:
                type = Long.TYPE;
                break;
            case Opcode.FLOAD_0:
            case Opcode.FLOAD_1:
            case Opcode.FLOAD_2:
            case Opcode.FLOAD_3:
            case Opcode.FLOAD:
                type = Float.TYPE;
                break;
            case Opcode.DLOAD_0:
            case Opcode.DLOAD_1:
            case Opcode.DLOAD_2:
            case Opcode.DLOAD_3:
            case Opcode.DLOAD:
                type = Double.TYPE;
                break;
            case Opcode.ALOAD_0:
            case Opcode.ALOAD_1:
            case Opcode.ALOAD_2:
            case Opcode.ALOAD_3:
            case Opcode.ALOAD:
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

    static RuntimeException notLambda(Opcode opcode) {
        return notLambda(opcode.name());
    }

    static RuntimeException notLambda(String opcodeName) {
        return new IllegalArgumentException("Not a lambda expression. Opcode " + opcodeName + " is illegal.");
    }

}
