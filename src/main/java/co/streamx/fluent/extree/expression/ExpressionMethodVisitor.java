package co.streamx.fluent.extree.expression;

import java.io.Serializable;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.SneakyThrows;
import lombok.val;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import co.streamx.fluent.extree.expression.ExpressionStack.BranchExpression;

/**
 *
 */

final class ExpressionMethodVisitor extends MethodVisitor {

    private static final Class<?>[] NumericTypeLookup = new Class<?>[]{Integer.TYPE, Long.TYPE, Float.TYPE,
            Double.TYPE};
    private static final Class<?>[] NumericTypeLookup2 = new Class<?>[]{Byte.TYPE, Character.TYPE, Short.TYPE};
    private static final String LambdaMetafactoryClassInternalName = LambdaMetafactory.class.getName()
            .replace('.', '/');

    private static final Map<Class<?>, Class<?>> _primitives;
    private static final Class<?>[] arrayTypesByCode = new Class[]{Boolean.TYPE, Character.TYPE, Float.TYPE,
            Double.TYPE, Byte.TYPE, Short.TYPE, Integer.TYPE, Long.TYPE};

    private ExpressionStack _exprStack;
    private List<Expression> _statements;
    private Expression[] _localVariables;

    private final HashMap<Label, List<ExpressionStack>> _branches = new HashMap<Label, List<ExpressionStack>>();

    private final ExpressionClassVisitor _classVisitor;
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

    ExpressionMethodVisitor(ExpressionClassVisitor classVisitor, Expression me, Class<?>[] argTypes) {
        super(Opcodes.ASM9);
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

    @Override
    public AnnotationVisitor visitAnnotation(String desc,
                                             boolean visible) {
        return null;
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
        return null;
    }

    @Override
    public void visitAttribute(Attribute attr) {

    }

    @Override
    public void visitCode() {
        _exprStack = new ExpressionStack();
    }

    @Override
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

    @Override
    public void visitFieldInsn(int opcode,
                               String owner,
                               String name,
                               String desc) {
        Expression e;
        boolean isSyntheticConstant = false;
        switch (opcode) {
            case Opcodes.GETFIELD:
                Expression instance = _exprStack.pop();
                try {
                    e = Expression.get(instance, name);
                } catch (NoSuchFieldException nsfe) {
                    throw new RuntimeException(nsfe);
                }
                if (instance.getExpressionType() == ExpressionType.Constant && instance.getResultType().isSynthetic())
                    isSyntheticConstant = true;
                break;
            case Opcodes.GETSTATIC:
                try {
                    Class<?> containingClass = _classVisitor.getClass(Type.getObjectType(owner));
                    e = Expression.get(containingClass, name);
                    if (containingClass.isSynthetic())
                        isSyntheticConstant = true;
                } catch (NoSuchFieldException nsfe) {
                    throw new RuntimeException(nsfe);
                }
                break;
            case Opcodes.PUTFIELD:
            case Opcodes.PUTSTATIC:
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

    @Override
    public void visitFrame(int type,
                           int nLocal,
                           Object[] local,
                           int nStack,
                           Object[] stack) {
        throw notLambda(type);
    }

    @Override
    public void visitIincInsn(int arg0,
                              int arg1) {
        throw notLambda(Opcodes.IINC);
    }

    @Override
    public void visitInsn(int opcode) {
        Expression e;
        Expression first;
        Expression second;
        switch (opcode) {
            case Opcodes.ARRAYLENGTH:
                e = Expression.arrayLength(_exprStack.pop());
                break;
            case Opcodes.ACONST_NULL:
                e = Expression.constant(null, Object.class);
                break;
            case Opcodes.IALOAD:
            case Opcodes.LALOAD:
            case Opcodes.FALOAD:
            case Opcodes.DALOAD:
            case Opcodes.AALOAD:
            case Opcodes.BALOAD:
            case Opcodes.CALOAD:
            case Opcodes.SALOAD:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.arrayIndex(second, first);
                break;
            case Opcodes.DCONST_0:
                e = Expression.constant(0d, Double.TYPE);
                break;
            case Opcodes.DCONST_1:
                e = Expression.constant(1d, Double.TYPE);
                break;
            case Opcodes.FCMPG:
            case Opcodes.FCMPL:
            case Opcodes.DCMPG:
            case Opcodes.DCMPL:
            case Opcodes.LCMP:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.subtract(second, first);
                break;
            case Opcodes.FCONST_0:
                e = Expression.constant(0f, Float.TYPE);
                break;
            case Opcodes.FCONST_1:
                e = Expression.constant(1f, Float.TYPE);
                break;
            case Opcodes.FCONST_2:
                e = Expression.constant(2f, Float.TYPE);
                break;
            case Opcodes.ICONST_M1:
                e = Expression.constant(-1, Integer.TYPE);
                break;
            case Opcodes.ICONST_0:
                e = Expression.constant(0, Integer.TYPE);
                break;
            case Opcodes.ICONST_1:
                e = Expression.constant(1, Integer.TYPE);
                break;
            case Opcodes.ICONST_2:
                e = Expression.constant(2, Integer.TYPE);
                break;
            case Opcodes.ICONST_3:
                e = Expression.constant(3, Integer.TYPE);
                break;
            case Opcodes.ICONST_4:
                e = Expression.constant(4, Integer.TYPE);
                break;
            case Opcodes.ICONST_5:
                e = Expression.constant(5, Integer.TYPE);
                break;
            case Opcodes.LCONST_0:
                e = Expression.constant(0l, Long.TYPE);
                break;
            case Opcodes.LCONST_1:
                e = Expression.constant(1l, Long.TYPE);
                break;
            case Opcodes.IADD:
            case Opcodes.LADD:
            case Opcodes.FADD:
            case Opcodes.DADD:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.add(second, first);
                break;
            case Opcodes.ISUB:
            case Opcodes.LSUB:
            case Opcodes.FSUB:
            case Opcodes.DSUB:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.subtract(second, first);
                break;
            case Opcodes.IMUL:
            case Opcodes.LMUL:
            case Opcodes.FMUL:
            case Opcodes.DMUL:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.multiply(second, first);
                break;
            case Opcodes.IDIV:
            case Opcodes.LDIV:
            case Opcodes.FDIV:
            case Opcodes.DDIV:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.divide(second, first);
                break;
            case Opcodes.IREM:
            case Opcodes.LREM:
            case Opcodes.FREM:
            case Opcodes.DREM:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.modulo(second, first);
                break;
            case Opcodes.INEG:
            case Opcodes.LNEG:
            case Opcodes.FNEG:
            case Opcodes.DNEG:
                first = _exprStack.pop();
                e = Expression.negate(first);
                break;
            case Opcodes.ISHL:
            case Opcodes.LSHL:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.leftShift(second, first);
                break;
            case Opcodes.ISHR:
            case Opcodes.LSHR:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.rightShift(second, first);
                break;
            case Opcodes.IUSHR:
            case Opcodes.LUSHR:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.rightShift(second, first);
                break;
            case Opcodes.IAND:
            case Opcodes.LAND:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.bitwiseAnd(second, first);
                break;
            case Opcodes.IOR:
            case Opcodes.LOR:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.bitwiseOr(second, first);
                break;
            case Opcodes.IXOR:
            case Opcodes.LXOR:
                first = _exprStack.pop();
                second = _exprStack.pop();
                e = Expression.exclusiveOr(second, first);
                break;
            case Opcodes.I2B:
            case Opcodes.I2C:
            case Opcodes.I2S:
                first = _exprStack.pop();
                e = Expression.convert(first, NumericTypeLookup2[opcode - Opcodes.I2B]);
                break;
            case Opcodes.I2L:
            case Opcodes.I2F:
            case Opcodes.I2D:
                first = _exprStack.pop();
                e = Expression.convert(first, NumericTypeLookup[opcode - Opcodes.I2L + 1]);
                break;
            case Opcodes.L2I:
            case Opcodes.L2F:
            case Opcodes.L2D:
                int l2l = opcode > Opcodes.L2I ? 1 : 0;
                first = _exprStack.pop();
                e = Expression.convert(first, NumericTypeLookup[opcode - Opcodes.L2I + l2l]);
                break;
            case Opcodes.F2I:
            case Opcodes.F2L:
            case Opcodes.F2D:
                int f2f = opcode == Opcodes.F2D ? 1 : 0;
                first = _exprStack.pop();
                e = Expression.convert(first, NumericTypeLookup[opcode - Opcodes.F2I + f2f]);
                break;
            case Opcodes.D2I:
            case Opcodes.D2L:
            case Opcodes.D2F:
                first = _exprStack.pop();
                e = Expression.convert(first, NumericTypeLookup[opcode - Opcodes.D2I]);
                break;
            case Opcodes.IRETURN:
            case Opcodes.LRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.ARETURN:

                go(null);

                return;
            case Opcodes.SWAP:
                first = _exprStack.pop();
                second = _exprStack.pop();
                _exprStack.push(first);
                _exprStack.push(second);
            case Opcodes.DUP:
            case Opcodes.DUP_X1:
            case Opcodes.DUP_X2:
            case Opcodes.DUP2:
            case Opcodes.DUP2_X1:
            case Opcodes.DUP2_X2:
                // our stack is not divided to words
                int base = (opcode - Opcodes.DUP) % 3;
                base++;
                dup(_exprStack, base, base - 1);
                return;
            case Opcodes.NOP:
            case Opcodes.RETURN:
                return;
            case Opcodes.POP:
            case Opcodes.POP2:
                if (_statements == null)
                    _statements = new ArrayList<>();
                _statements.add(_exprStack.pop());
                return;
            case Opcodes.AASTORE:
            case Opcodes.BASTORE:
            case Opcodes.CASTORE:
            case Opcodes.DASTORE:
            case Opcodes.FASTORE:
            case Opcodes.IASTORE:
            case Opcodes.LASTORE:
            case Opcodes.SASTORE:
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

    @Override
    public void visitIntInsn(int opcode,
                             int operand) {
        switch (opcode) {
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                _exprStack.push(Expression.constant(operand, Integer.TYPE));
                break;
            case Opcodes.NEWARRAY:
                _exprStack.push(createNewArrayInitExpression(opcode, arrayTypesByCode[operand - Opcodes.T_BOOLEAN]));
                break;
            default:
                throw notLambda(opcode);
        }
    }

    @Override
    public void visitJumpInsn(int opcode,
                              Label label) {
        int etype;
        switch (opcode) {
            case Opcodes.GOTO:

                go(label);

                return;
            default:
            case Opcodes.JSR:
                throw notLambda(opcode);
            case Opcodes.IFEQ:
                etype = ExpressionType.NotEqual; // Equal
                pushZeroConstantOrReduce();
                break;
            case Opcodes.IFNE:
                etype = ExpressionType.Equal; // NotEqual
                pushZeroConstantOrReduce();
                break;
            case Opcodes.IFLT:
                etype = ExpressionType.GreaterThanOrEqual; // LessThan
                pushZeroConstantOrReduce();
                break;
            case Opcodes.IFGE:
                etype = ExpressionType.LessThan; // GreaterThanOrEqual
                pushZeroConstantOrReduce();
                break;
            case Opcodes.IFGT:
                etype = ExpressionType.LessThanOrEqual; // GreaterThan
                pushZeroConstantOrReduce();
                break;
            case Opcodes.IFLE:
                etype = ExpressionType.GreaterThan; // LessThanOrEqual
                pushZeroConstantOrReduce();
                break;
            case Opcodes.IF_ICMPEQ:
            case Opcodes.IF_ACMPEQ: // ??
                etype = ExpressionType.NotEqual; // Equal
                break;
            case Opcodes.IF_ICMPNE:
            case Opcodes.IF_ACMPNE: // ??
                etype = ExpressionType.Equal; // NotEqual
                break;
            case Opcodes.IF_ICMPLT:
                etype = ExpressionType.GreaterThanOrEqual; // LessThan
                break;
            case Opcodes.IF_ICMPGE:
                etype = ExpressionType.LessThan; // GreaterThanOrEqual
                break;
            case Opcodes.IF_ICMPGT:
                etype = ExpressionType.LessThanOrEqual; // GreaterThan
                break;
            case Opcodes.IF_ICMPLE:
                etype = ExpressionType.GreaterThan; // LessThanOrEqual
                break;
            case Opcodes.IFNULL:
            case Opcodes.IFNONNULL:
                Expression e = Expression.isNull(_exprStack.pop());
                if (opcode == Opcodes.IFNULL) // IFNONNULL
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

    @Override
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

    @Override
    public void visitLdcInsn(Object cst) {
        Class<?> type = _primitives.get(cst.getClass());
        if (type == null) {
            type = cst.getClass();
            if (type == Type.class) {
                type = Class.class;
                cst = _classVisitor.getClass((Type) cst);
            }
        }
        _exprStack.push(Expression.constant(cst, type));
    }

    @Override
    public void visitLineNumber(int line,
                                Label start) {

    }

    @Override
    public void visitLocalVariable(String name,
                                   String desc,
                                   String signature,
                                   Label start,
                                   Label end,
                                   int index) {
        throw notLambda(-1);
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt,
                                      int[] keys,
                                      Label[] labels) {
        throw notLambda(Opcodes.LOOKUPSWITCH);
    }

    @Override
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

    @SneakyThrows
    private void makeConcatWithConstants(String descriptor, Object[] bootstrapMethodArguments) {
        val argsTypes = Type.getArgumentTypes(descriptor);
        Class<?>[] parameterTypes = getParameterTypes(argsTypes);
        Expression[] params = new Expression[parameterTypes.length];
        for (var i = params.length - 1; i >= 0; i--)
            params[i] = _exprStack.pop();
        var recipe = (String) bootstrapMethodArguments[0];

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

            if (c == TAG_CONST) b.append(bootstrapMethodArguments[curConst++]);
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

    @Override
    public void visitInvokeDynamicInsn(String name,
                                       String descriptor,
                                       Handle bootstrapMethodHandle,
                                       Object... bootstrapMethodArguments) {

        String bootMethod = bootstrapMethodHandle.getName();
        if (bootstrapMethodHandle.getOwner().equals("java/lang/invoke/StringConcatFactory") && name.equals("makeConcatWithConstants")) {
            makeConcatWithConstants(descriptor, bootstrapMethodArguments);
            return;
        }
        if (!bootstrapMethodHandle.getOwner().equals(LambdaMetafactoryClassInternalName)
                || !"Metafactory".regionMatches(true, 0, bootMethod, bootMethod.length() - "Metafactory".length(),
                "Metafactory".length())) {
            throw new UnsupportedOperationException("Unsupported bootstrapMethodHandle: " + bootstrapMethodHandle);
        }

        val handle = (Handle) bootstrapMethodArguments[1];
        val internalName = handle.getOwner();
        val objectType = Type.getObjectType(internalName);
        val containingClass = _classVisitor.getClass(objectType);

        val hasThis = handle.getTag() == Opcodes.H_INVOKEINTERFACE || handle.getTag() == Opcodes.H_INVOKESPECIAL
                || handle.getTag() == Opcodes.H_INVOKEVIRTUAL;

        Expression optionalThis = hasThis ? Expression.parameter(containingClass, 0) : null;
        val methodDescriptor = handle.getDesc();
        val targetParameterTypes = getParameterTypes(Type.getArgumentTypes(methodDescriptor));
        val methodName = handle.getName();
        Method method;
        try {
            method = containingClass.getDeclaredMethod(methodName, targetParameterTypes);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        var params = Expression.getParameters(method);
        val member = Expression.member(ExpressionType.MethodAccess, optionalThis, method,
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

        val call = Expression.invoke(member, params);
        if (hasThis) {
            params.add(0, (ParameterExpression) optionalThis);
        }
        val methodLoader = _classVisitor.getLoader();
        val lambda = Expression.lambda(call.getResultType(), call, params, Collections.emptyList(), null,
                method.isSynthetic() ? () -> ExpressionClassCracker.get()
                        .lambdaFromClassLoader(methodLoader, internalName, optionalThis,
                                methodName,
                                methodDescriptor) : null);

        val argsTypes = Type.getArgumentTypes(descriptor);
        if (argsTypes.length == 0) {
            _exprStack.push(lambda);
            return;
        }

        val arguments = createArguments(argsTypes);

        Class<?>[] parameterTypes = getParameterTypes(argsTypes);
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

    @Override
    public void visitMethodInsn(int opcode,
                                String owner,
                                String name,
                                String desc,
                                boolean itf) {

        Type[] argsTypes = Type.getArgumentTypes(desc);

        // Class<?>[] parameterTypes = getParameterTypes(argsTypes);

        Expression[] arguments = createArguments(argsTypes);

        Expression e;

        switch (opcode) {
            case Opcodes.INVOKESPECIAL:
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
            case Opcodes.INVOKEVIRTUAL:
            case Opcodes.INVOKEINTERFACE:
                try {
                    Class<?> lambdaClass = _classVisitor.getClass(Type.getObjectType(owner));
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

            case Opcodes.INVOKESTATIC:
                Class<?>[] parameterTypes = getParameterTypes(argsTypes);
                convertArguments(arguments, parameterTypes);
                try {
                    Class<?> targetType = _classVisitor.getClass(Type.getObjectType(owner));
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

    private Expression[] createArguments(Type[] argsTypes) {
        Expression[] arguments = new Expression[argsTypes.length];
        for (int i = argsTypes.length; i > 0; ) {
            i--;
            arguments[i] = _exprStack.pop();
        }
        return arguments;
    }

    private Class<?>[] getParameterTypes(Type[] argsTypes) {
        Class<?>[] parameterTypes = new Class<?>[argsTypes.length];
        for (int i = 0; i < argsTypes.length; i++)
            parameterTypes[i] = _classVisitor.getClass(argsTypes[i]);
        return parameterTypes;
    }

    // @Overrides
    @Override
    public void visitMultiANewArrayInsn(String desc,
                                        int dims) {
        throw notLambda(Opcodes.MULTIANEWARRAY);
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int arg0,
                                                      String arg1,
                                                      boolean arg2) {
        return null;
    }

    @Override
    public void visitTableSwitchInsn(int min,
                                     int max,
                                     Label dflt,
                                     Label... labels) {
        throw notLambda(Opcodes.TABLESWITCH);
    }

    @Override
    public void visitTryCatchBlock(Label start,
                                   Label end,
                                   Label handler,
                                   String type) {
        throw notLambda(-2);
    }

    @Override
    public void visitTypeInsn(int opcode,
                              String type) {
        Class<?> resultType = _classVisitor.getClass(Type.getObjectType(type));
        Expression e;
        switch (opcode) {
            case Opcodes.NEW:
                e = Expression.constant(null, resultType);
                break;
            case Opcodes.CHECKCAST:
                if (resultType == Object.class)
                    // there is no point in casting to object
                    return;
                e = Expression.convert(_exprStack.pop(), resultType);
                break;
            case Opcodes.ANEWARRAY:
                e = createNewArrayInitExpression(opcode, resultType);
                break;

            case Opcodes.INSTANCEOF:
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

    @Override
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
            case Opcodes.ISTORE:
            case Opcodes.LSTORE:
            case Opcodes.FSTORE:
            case Opcodes.DSTORE:
            case Opcodes.ASTORE:
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
            case Opcodes.RET:
            default:
                throw notLambda(opcode);
            case Opcodes.ILOAD:
                type = Integer.TYPE;
                break;
            case Opcodes.LLOAD:
                type = Long.TYPE;
                break;
            case Opcodes.FLOAD:
                type = Float.TYPE;
                break;
            case Opcodes.DLOAD:
                type = Double.TYPE;
                break;
            case Opcodes.ALOAD:
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
        Field[] ops = Opcodes.class.getFields();
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
