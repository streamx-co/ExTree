package co.streamx.fluent.extree.expression;

import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.Collections;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a visitor or rewriter for expression trees.
 */

final class ExpressionClassVisitor //extends ClassVisitor 
        implements ExpressionResolver {

    @Getter
    private final ClassLoader loader;
    private final Expression _me;
    private final String _method;
    private final String _methodDesc;
    private final boolean _synthetic;

    @Getter
    @Setter
    private Expression result;
    @Getter
    private Class<?> type;
    private Class<?>[] _argTypes;
    private Signature _objectType;

    @Getter
    @Setter
    private List<Expression> statements;
    @Getter
    @Setter
    private List<Expression> locals;

    ParameterExpression[] getParams() {
        ParameterExpression[] params = new ParameterExpression[_argTypes.length];
        for (int i = 0; i < params.length; i++)
            params[i] = Expression.parameter(_argTypes[i], i);
        return params;
    }

    public ExpressionClassVisitor(ClassLoader loader, Expression instance, String method,
                                  String methodDescriptor, boolean synthetic) {
        this.loader = loader;
        _me = instance;
        _method = method;
        _methodDesc = methodDescriptor;
        _synthetic = synthetic;
    }

    public Class<?> getClass(Signature t) {
        try {
            return switch (t) {
                case Signature.BaseTypeSig b -> switch (b.baseType()) {
//                case '[', 'L' -> TypeKind.ReferenceType;
                    case 'B' -> Byte.TYPE;
                    case 'C' -> Character.TYPE;
                    case 'Z' -> Boolean.TYPE;
                    case 'S' -> Short.TYPE;
                    case 'I' -> Integer.TYPE;
                    case 'F' -> Float.TYPE;
                    case 'J' -> Long.TYPE;
                    case 'D' -> Double.TYPE;
                    case 'V' -> Void.TYPE;
                    default -> throw new IllegalArgumentException("Bad type: " + t);
                };
                case Signature.ArrayTypeSig a -> getClass(a.componentSignature()).arrayType();
                case Signature.ClassTypeSig c -> Class.forName(c.className().replace('/', '.'), false, loader);
                default -> throw new IllegalArgumentException("Bad type: " + t);
            };

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    //    @Override
    public ExpressionMethodVisitor visitMethod(int access,
                                               String name,
                                               String desc,
                                               String signature,
                                               String[] exceptions) {

        if (!_method.equals(name) || !_methodDesc.equals(desc))
            return null;


        var sig = MethodSignature.parseFrom(desc);

        var ret = sig.result();

        type = getClass(ret);

        var args = sig.arguments();
        Class<?>[] argTypes = new Class<?>[args.size()];

        for (int i = 0; i < args.size(); i++)
            argTypes[i] = getClass(args.get(i));

        if (_synthetic && _objectType != null && (access & AccessFlag.SYNTHETIC.mask()) == 0) {
            // not synthetic - do not parse
            try {
                boolean isStatic = (access & AccessFlag.STATIC.mask()) != 0;
                boolean isCtor = !isStatic && "<init>".equals(name);
                Class<?> implClass = getClass(_objectType);
                Expression[] arguments = new Expression[argTypes.length];
                int parameterBase;
                Expression instance;

                if (isStatic || isCtor) {
                    parameterBase = 0;
                    instance = null;

                    if (isCtor)
                        type = implClass;

                } else {
                    if (_me != null) {
                        instance = _me;
                        parameterBase = _me instanceof ParameterExpression ? 1 : 0;
                    } else {
                        parameterBase = 1;
                        instance = Expression.parameter(implClass, 0);
                    }
                }

                for (int i = 0; i < argTypes.length; i++) {
                    Class<?> argType = argTypes[i];
                    arguments[i] = Expression.parameter(argType, i + parameterBase);
                }
                result = isCtor ? Expression.newInstance(implClass, argTypes, arguments)
                        : isStatic ? Expression.invoke(implClass, name, argTypes, arguments)
                        : Expression.invoke(instance, name, argTypes, arguments);
                locals = Collections.emptyList();
                if (parameterBase == 0) {
                    _argTypes = argTypes;
                } else {
                    _argTypes = new Class<?>[argTypes.length + 1];
                    _argTypes[0] = implClass;
                    System.arraycopy(argTypes, 0, _argTypes, 1, argTypes.length);
                }

                return null;
            } catch (Throwable e) {
                // fallback;
            }
        }

        Expression me = _me;

        if ((access & AccessFlag.STATIC.mask()) == 0) {
            if (me != null && !isConstant(me)) {
                Class<?>[] newArgTypes = new Class<?>[argTypes.length + 1];
                newArgTypes[0] = me.getResultType();
                System.arraycopy(argTypes, 0, newArgTypes, 1, argTypes.length);
                argTypes = newArgTypes;
                me = null;
            }
        } else {
            me = null;
        }

        _argTypes = argTypes;

        return new ExpressionMethodVisitor(this, me, argTypes);
    }

    private static boolean isConstant(Expression e) {
        while (e.getExpressionType() == ExpressionType.Convert)
            e = ((UnaryExpression) e).getFirst();

        return e instanceof ConstantExpression;
    }

    //    @Override
    public void visit(int version,
                      int access,
                      String name,
                      ClassDesc classDesc,
                      String superName,
                      String[] interfaces) {

        // potentially a method reference - store object type
        if ((access & AccessFlag.SYNTHETIC.mask()) == 0)
            _objectType = Signature.of(classDesc);
    }
}
