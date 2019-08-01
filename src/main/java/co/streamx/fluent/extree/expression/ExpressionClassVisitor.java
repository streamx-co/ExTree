package co.streamx.fluent.extree.expression;

import java.util.Collections;
import java.util.List;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Represents a visitor or rewriter for expression trees.
 * 
 * 
 */

final class ExpressionClassVisitor extends ClassVisitor {

    private final ClassLoader _loader;
    private final Expression _me;
    private final String _method;
    private final String _methodDesc;
    private final boolean _synthetic;

    private Expression _result;
    private Class<?> _type;
    private Class<?>[] _argTypes;
    private Type _objectType;

    private List<Expression> statements;
    private List<Expression> locals;

    Expression getResult() {
        return _result;
    }

    void setResult(Expression result) {
        _result = result;
    }

    void setStatements(List<Expression> statements) {
        this.statements = statements;
    }

    List<Expression> getStatements() {
        return statements;
    }

    void setLocals(List<Expression> locals) {
        this.locals = locals;
    }

    List<Expression> getLocals() {
        return locals;
    }

    Class<?> getType() {
        return _type;
    }

    ParameterExpression[] getParams() {
        ParameterExpression[] params = new ParameterExpression[_argTypes.length];
        for (int i = 0; i < params.length; i++)
            params[i] = Expression.parameter(_argTypes[i], i);
        return params;
    }

    public ExpressionClassVisitor(ClassLoader loader, Expression instance, String method,
            String methodDescriptor, boolean synthetic) {
        super(Opcodes.ASM7);
        _loader = loader;
        _me = instance;
        _method = method;
        _methodDesc = methodDescriptor;
        _synthetic = synthetic;
    }

    ClassLoader getLoader() {
        return _loader;
    }

    Class<?> getClass(Type t) {
        try {
            switch (t.getSort()) {
            case Type.BOOLEAN:
                return Boolean.TYPE;
            case Type.CHAR:
                return Character.TYPE;
            case Type.BYTE:
                return Byte.TYPE;
            case Type.SHORT:
                return Short.TYPE;
            case Type.INT:
                return Integer.TYPE;
            case Type.FLOAT:
                return Float.TYPE;
            case Type.LONG:
                return Long.TYPE;
            case Type.DOUBLE:
                return Double.TYPE;
            case Type.VOID:
                return Void.TYPE;
            }
            String cn = t.getInternalName();
            cn = cn != null ? cn.replace('/', '.') : t.getClassName();

            return Class.forName(cn, false, _loader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public MethodVisitor visitMethod(int access,
                                     String name,
                                     String desc,
                                     String signature,
                                     String[] exceptions) {

        if (!_method.equals(name) || !_methodDesc.equals(desc))
            return null;

        Type ret = Type.getReturnType(desc);

        _type = getClass(ret);

        Type[] args = Type.getArgumentTypes(desc);
        Class<?>[] argTypes = new Class<?>[args.length];

        for (int i = 0; i < args.length; i++)
            argTypes[i] = getClass(args[i]);

        if (_synthetic && _objectType != null && (access & Opcodes.ACC_SYNTHETIC) == 0) {
            // not synthetic - do not parse
            try {
                boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
                boolean isCtor = !isStatic && "<init>".equals(name);
                Class<?> implClass = getClass(_objectType);
                Expression[] arguments = new Expression[argTypes.length];
                int parameterBase;
                Expression instance;

                if (isStatic || isCtor) {
                    parameterBase = 0;
                    instance = null;

                    if (isCtor)
                        _type = implClass;

                } else {
                    if (_me != null) {
                        parameterBase = 0;
                        instance = _me;
                    } else {
                        parameterBase = 1;
                        instance = Expression.parameter(implClass, 0);
                    }
                }

                for (int i = 0; i < argTypes.length; i++) {
                    Class<?> argType = argTypes[i];
                    arguments[i] = Expression.parameter(argType, i + parameterBase);
                }
                _result = isCtor ? Expression.newInstance(implClass, argTypes, arguments)
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

        if ((access & Opcodes.ACC_STATIC) == 0) {
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

    @Override
    public void visit(int version,
                      int access,
                      String name,
                      String signature,
                      String superName,
                      String[] interfaces) {

        // potentially a method reference - store object type
        if ((access & Opcodes.ACC_SYNTHETIC) == 0)
            _objectType = Type.getObjectType(name);
        super.visit(version, access, name, signature, superName, interfaces);
    }
}
