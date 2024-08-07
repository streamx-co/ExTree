package co.streamx.fluent.extree.expression;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

class ExpressionClassCracker {

    private static final String DUMP_FOLDER_SYSTEM_PROPERTY = "jdk.internal.lambda.dumpProxyClasses";
    private static final URLClassLoader lambdaClassLoader;
    private static final String lambdaClassLoaderCreationError;

    private static final ExpressionClassCracker instance = new ExpressionClassCracker();

    public static ExpressionClassCracker get() {
        return instance;
    }

    static {
        String folderPath = System.getProperty(DUMP_FOLDER_SYSTEM_PROPERTY);
        if (folderPath == null) {
            lambdaClassLoaderCreationError = "Ensure that the '" + DUMP_FOLDER_SYSTEM_PROPERTY
                    + "' system property is properly set.";
            lambdaClassLoader = null;
        } else {
            File folder = new File(folderPath);
            if (!folder.isDirectory()) {
                lambdaClassLoaderCreationError = "Ensure that the '" + DUMP_FOLDER_SYSTEM_PROPERTY
                        + "' system property is properly set (" + folderPath + " does not exist).";
                lambdaClassLoader = null;
            } else {
                URL folderURL;
                try {
                    folderURL = folder.toURI().toURL();
                } catch (MalformedURLException mue) {
                    throw new RuntimeException(mue);
                }

                lambdaClassLoaderCreationError = null;
                lambdaClassLoader = new URLClassLoader(new URL[] { folderURL });
            }
        }
    }

    private ExpressionClassCracker() {
    }

    private static final class ParameterReplacer extends SimpleExpressionVisitor {
        private List<Integer> paramIndices;
        private final Object lambda;
        private LambdaExpression<?> parsedLambda;
        private List<Integer> prevParamIndices;

        public ParameterReplacer(int paramIndex, Object lambda) {
            this.paramIndices = Arrays.asList(paramIndex);
            this.lambda = lambda;
        }

        public LambdaExpression<?> getParsedLambda() {
            return parsedLambda;
        }

        @Override
        public Expression visit(InvocationExpression e) {
            if (this.paramIndices.isEmpty())
                return e;
            prevParamIndices = this.paramIndices;
            try {
                return super.visit(e);
            } finally {
                this.paramIndices = prevParamIndices;
            }
        }

        @Override
        protected List<Expression> visitArguments(List<Expression> original) {
            try {
                return super.visitArguments(original);
            } finally {
                List<Integer> paramIndices = this.paramIndices;
                List<Integer> newParamIndices = new ArrayList<>();
                for (int i = 0; i < original.size(); i++) {
                    Expression e = original.get(i);
                    if (e.getExpressionType() == ExpressionType.Parameter) {
                        ParameterExpression p = (ParameterExpression) e;
                        if (paramIndices.contains(p.getIndex()))
                            newParamIndices.add(i);
                    }
                }

                this.paramIndices = newParamIndices;
            }
        }

        @Override
        public Expression visit(MemberExpression e) {
            Expression instance = e.getInstance();
            if (instance != null && instance.getExpressionType() == ExpressionType.Parameter) {
                int index = ((ParameterExpression) instance).getIndex();
                if (prevParamIndices.contains(index)) {
                    if (lambda != null && parsedLambda == null) {
                        Method method = (Method) e.getMember();
                        try {
                            method = lambda.getClass().getDeclaredMethod(method.getName(), method.getParameterTypes());
                        } catch (NoSuchMethodException nsme) {
                            // should never happen
                            throw new RuntimeException(nsme);
                        }
                        parsedLambda = ExpressionClassCracker.get().lambda(lambda, method, true);
                    }
                    return Expression.delegate(e.getResultType(), Expression.parameter(LambdaExpression.class, index),
                            visitParameters(e.getParameters()));
                }
            }
            return super.visit(e);
        }

    }

    private static class SerializedLambdaObjectExtractor extends ObjectOutputStream {

        private SerializedLambda serializedLambda;

        public SerializedLambdaObjectExtractor() throws IOException {
            super(new ByteArrayOutputStream(8));
            enableReplaceObject(true);
        }

        public SerializedLambda extract(Object lambda) throws IOException {
            writeObject(lambda);
            return serializedLambda;
        }

        @Override
        protected Object replaceObject(Object obj) throws IOException {
            serializedLambda = (SerializedLambda) obj;
            return null;
        }
    }

    LambdaExpression<?> lambda(Object lambda,
                               boolean synthetic) {
        return lambda(lambda, null, synthetic);
    }

    private LambdaExpression<?> lambda(Object lambda,
                                       Method lambdaMethod,
                                       boolean synthetic) {
        Class<?> lambdaClass = lambda.getClass();
        if (!isFunctional(lambdaClass))
            throw new IllegalArgumentException("The requested object is not a Java lambda");

        if (lambda instanceof Serializable) {

            try (SerializedLambdaObjectExtractor extractor = new SerializedLambdaObjectExtractor()) {
                SerializedLambda extracted = extractor.extract(lambda);

                ClassLoader lambdaClassLoader = lambdaClass.getClassLoader();
                return lambda(extracted, lambdaClassLoader, synthetic);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }

        return lambdaFromFileSystem(lambda, lambdaMethod, null);
    }

    LambdaExpression<?> lambdaFromFileSystem(Object lambda,
                                             Method lambdaMethod,
                                             ClassLoader classLoader) {
        ExpressionClassVisitor lambdaVisitor = parseFromFileSystem(lambda, lambdaMethod, classLoader);

        return createLambda(lambdaVisitor, null);
    }

    LambdaExpression<?> lambdaFromClassLoader(ClassLoader classLoader,
                                              String className,
                                              Expression instance,
                                              String method,
                                              String methodDescriptor) {

        SerializedDescriptor desc = new SerializedDescriptor(className, method, methodDescriptor, -1, methodDescriptor);
        boolean isCacheable = instance == null || instance.getResultType().isSynthetic();
        if (isCacheable) {
            LambdaExpression<?> cached = cache.get(desc);
            if (cached != null) {
//                System.out.println("Cache hit #2: " + cached);
                return cached;
            }
        }

        ExpressionClassVisitor lambdaVisitor = parseClass(classLoader, className, instance, method, methodDescriptor);

        LambdaExpression<?> parsed = createLambda(lambdaVisitor, desc);

        if (isCacheable) {
            LambdaExpression<?> cached = cache.putIfAbsent(desc, parsed);
            if (cached != null)
                parsed = cached;
        }

        return parsed;
    }

    private LambdaExpression<?> createLambda(ExpressionClassVisitor lambdaVisitor,
                                             SerializedDescriptor key) {
        Expression lambdaExpression = lambdaVisitor.getResult();
        Class<?> lambdaType = lambdaVisitor.getType();
        List<ParameterExpression> lambdaParams = Arrays.asList(lambdaVisitor.getParams());

        Expression stripped = lambdaType == Void.TYPE ? null : stripConvertExpressions(lambdaExpression);

        List<Expression> block = lambdaVisitor.getStatements();
        if (block != null && !block.isEmpty()) {

            block = new ArrayList<>(block);
            if (lambdaExpression != null)
                block.add(lambdaExpression);

            lambdaExpression = Expression.block(lambdaType, block);
        } else if (stripped instanceof InvocationExpression) {

            InvocationExpression invocation = (InvocationExpression) stripped;
            InvocableExpression target = invocation.getTarget();
            if (target instanceof LambdaExpression<?>) {
                REDUCE_CHECK: for (;;) {
                    if (!lambdaType.isAssignableFrom(target.getResultType()))
                        break;
                    List<ParameterExpression> params = lambdaParams;
                    List<Expression> args = invocation.getArguments();
                    int psize = params.size();
                    if (psize != args.size())
                        break;
                    for (int i = 0; i < psize; i++) {
                        Expression arg = args.get(i);
                        if (!(arg instanceof ParameterExpression))
                            break REDUCE_CHECK;
                        ParameterExpression parg = (ParameterExpression) arg;
                        ParameterExpression param = params.get(i);
                        if (parg.getIndex() != param.getIndex())
                            break REDUCE_CHECK;
                        if (!param.getResultType().isAssignableFrom(parg.getResultType()))
                            break REDUCE_CHECK;
                    }
                    return (LambdaExpression<?>) target;
                }
            }

        }

        Expression actualExpression = TypeConverter.convert(lambdaExpression, lambdaType);
        return Expression.lambda(lambdaType, actualExpression, lambdaParams, lambdaVisitor.getLocals(), key);
    }

    LambdaExpression<?> lambda(SerializedLambda extracted,
                               ClassLoader lambdaClassLoader) {
        return lambda(extracted, lambdaClassLoader, true);
    }

    @Data
    @EqualsAndHashCode
    @RequiredArgsConstructor
    private static class SerializedDescriptor {

        public SerializedDescriptor(SerializedLambda lambda) {
            this.implClass = lambda.getImplClass();
            this.implMethodName = lambda.getImplMethodName();
            this.implMethodSignature = lambda.getImplMethodSignature();
            this.implMethodKind = lambda.getImplMethodKind();
            this.instantiatedMethodType = lambda.getInstantiatedMethodType();
        }

        public SerializedDescriptor withImplClass(String implClass) {
            return new SerializedDescriptor(implClass, implClass, implClass, implMethodKind, implClass);
        }

        private final String implClass;
        private final String implMethodName;
        private final String implMethodSignature;
        private final int implMethodKind;
        private final String instantiatedMethodType;
    }

    private static final Map<SerializedDescriptor, LambdaExpression<?>> cache = new ConcurrentHashMap<>();

    LambdaExpression<?> lambda(SerializedLambda extracted,
                               ClassLoader lambdaClassLoader,
                               boolean synthetic) {
        int capturedLength = extracted.getCapturedArgCount();
        SerializedDescriptor desc = new SerializedDescriptor(extracted);

        boolean hasThis = extracted.getImplMethodKind() == MethodHandleInfo.REF_invokeInterface
                || extracted.getImplMethodKind() == MethodHandleInfo.REF_invokeSpecial
                || extracted.getImplMethodKind() == MethodHandleInfo.REF_invokeVirtual;

        Expression instance;

        if (hasThis) {
            if (capturedLength == 0) {
                try {
                    instance = Expression
                            .parameter(lambdaClassLoader.loadClass(extracted.getImplClass().replace('/', '.')), 0);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            } else {
                Object arg0 = extracted.getCapturedArg(0);
                if (desc != null)
                    desc = desc.withImplClass(arg0.getClass().getName());
                instance = Expression.constant(arg0);
            }
        } else {
            instance = null;
        }

        boolean noNeedHandleCapturedArgs = capturedLength == 0 || (hasThis && capturedLength <= 1);
        boolean isCacheable = noNeedHandleCapturedArgs && (!hasThis || instance.getResultType().isSynthetic());
        if (isCacheable) {
            LambdaExpression<?> cached = cache.get(desc);
            if (cached != null) {
//                System.out.println("Cache hit #1: " + cached);
                return cached;
            }
        }

        ExpressionClassVisitor actualVisitor = parseClass(lambdaClassLoader, extracted.getImplClass(), 
                instance, extracted.getImplMethodName(), extracted.getImplMethodSignature(), synthetic);

        final Class<?> type = actualVisitor.getType();
        Expression reducedExpression = type == Void.TYPE ? actualVisitor.getResult()
                : TypeConverter.convert(actualVisitor.getResult(), type);

        List<Expression> block = actualVisitor.getStatements();
        if (block != null && !block.isEmpty()) {

            block = new ArrayList<>(block);
            if (reducedExpression != null)
                block.add(reducedExpression);

            reducedExpression = Expression.block(type, block);
        }

        ParameterExpression[] params;

        // in case there is no captured args, we my assume the instantiated method signature to be the most accurate,
        // e.g. handle the case of a parameter for this
        if (capturedLength == 0) {

            Type[] argTypes = Type.getArgumentTypes(extracted.getInstantiatedMethodType());
            params = new ParameterExpression[argTypes.length];

            for (int i = 0; i < argTypes.length; i++)
                params[i] = Expression.parameter(actualVisitor.getClass(argTypes[i]), i);
        } else {
            params = actualVisitor.getParams();
        }

        LambdaExpression<?> extractedLambda = Expression.lambda(type, reducedExpression,
                Collections.unmodifiableList(Arrays.asList(params)), actualVisitor.getLocals(), desc);

        if (noNeedHandleCapturedArgs) {
            if (isCacheable) {
                LambdaExpression<?> cached = cache.putIfAbsent(desc, extractedLambda);
                if (cached != null)
                    extractedLambda = cached;
            }
            return extractedLambda;
        }

        List<Expression> args = new ArrayList<>(params.length);

        for (int i = hasThis ? 1 : 0; i < capturedLength; i++) {
            Object arg = extracted.getCapturedArg(i);
            if (arg instanceof SerializedLambda) {
                SerializedLambda argLambda = (SerializedLambda) arg;

                LambdaExpression<?> argExtractedLambda = lambda(argLambda, lambdaClassLoader);

                extractedLambda = (LambdaExpression<?>) extractedLambda
                        .accept(new ParameterReplacer(args.size(), null));

                arg = argExtractedLambda;
            }
            args.add(Expression.constant(arg));
        }

        List<ParameterExpression> finalParams = new ArrayList<>(params.length - capturedLength);
        int boundArgs = args.size();
        for (int y = boundArgs; y < params.length; y++) {
            ParameterExpression param = params[y];
            ParameterExpression arg = Expression.parameter(param.getResultType(), y - boundArgs);
            args.add(arg);
            finalParams.add(arg);
        }

//        cached = cache.putIfAbsent(desc, extractedLambda);
//        if (cached != null)
//            extractedLambda = cached;

        InvocationExpression newTarget = Expression.invoke(extractedLambda, args);

        return Expression.lambda(type, newTarget, Collections.unmodifiableList(finalParams),
                Collections.emptyList(), desc);
    }

    @SuppressWarnings("unchecked")
    <T extends Expression> T parseSyntheticArguments(T expression,
                                                     List<Expression> arguments) {

        for (int i = 0; i < arguments.size(); i++) {
            Expression e = arguments.get(i);
            if (e.getExpressionType() == ExpressionType.Constant) {
                Object value = ((ConstantExpression) e).getValue();
                if (value != null && isFunctional(value.getClass())) {
                    ParameterReplacer replacer = new ParameterReplacer(i, value);
                    expression = (T) expression.accept(replacer);
                    if (replacer.getParsedLambda() != null) {
                        arguments.set(i, Expression.constant(replacer.getParsedLambda()));
                    }
                }
            }
        }
        return expression;
    }

    private static boolean isFunctional(Class<?> clazz) {
        if (clazz.isSynthetic())
            return true;

        for (Class<?> i : clazz.getInterfaces())
            if (i.isAnnotationPresent(FunctionalInterface.class))
                return true;

        return false;
    }

    ExpressionClassVisitor parseFromFileSystem(Object lambda,
                                               Method lambdaMethod,
                                               ClassLoader classLoader) {
        if (classLoader == null) {
            if (lambdaClassLoader == null)
                throw new RuntimeException(lambdaClassLoaderCreationError);
            classLoader = lambdaClassLoader;
        }

        Class<? extends Object> lambdaClass;

        if (lambdaMethod == null) {
            lambdaClass = lambda.getClass();
            lambdaMethod = findFunctionalMethod(lambdaClass);
        } else {
            lambdaClass = lambdaMethod.getDeclaringClass();
        }
        String lambdaClassName = lambdaClassName(lambdaClass);
        return parseClass(classLoader, lambdaClassName,
                lambda instanceof Expression ? (Expression) lambda : Expression.constant(lambda),
                lambdaMethod);
    }

    private String lambdaClassName(Class<?> lambdaClass) {
        String lambdaClassName = lambdaClass.getName();
        int lastIndexOfSlash = lambdaClassName.lastIndexOf('/');
        String className = lastIndexOfSlash > 0 ? lambdaClassName.substring(0, lastIndexOfSlash) : lambdaClassName;
        return className;
    }

    private String classFilePath(String className) {
        return className.replace('.', '/') + ".class";
    }

    private Method findFunctionalMethod(Class<?> functionalClass) {
        for (Method m : functionalClass.getMethods()) {
            if (!m.isDefault()) {
                return m;
            }
        }
        throw new IllegalArgumentException("Not a lambda expression. No non-default method.");
    }

    private ExpressionClassVisitor parseClass(ClassLoader classLoader,
                                              String className,
                                              Expression instance,
                                              Method method) {
        return parseClass(classLoader, className, instance, method.getName(), Type.getMethodDescriptor(method), false);
    }

    private ExpressionClassVisitor parseClass(ClassLoader classLoader,
                                              String className,
                                              Expression instance,
                                              String method,
                                              String methodDescriptor) {
        return parseClass(classLoader, className, instance, method, methodDescriptor, true);
    }

    private ExpressionClassVisitor parseClass(ClassLoader classLoader,
                                              String className,
                                              Expression instance,
                                              String method,
                                              String methodDescriptor,
                                              boolean synthetic) {
        String classFilePath = classFilePath(className);
        ExpressionClassVisitor visitor = new ExpressionClassVisitor(classLoader, instance, method, methodDescriptor,
                synthetic);
        try (InputStream classStream = getResourceAsStream(classLoader, classFilePath)) {
            ClassReader reader = new ClassReader(classStream);
            reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return visitor;
        } catch (IOException e) {
            throw new RuntimeException("error parsing class file " + classFilePath, e);
        }
    }

    private InputStream getResourceAsStream(ClassLoader classLoader,
                                            String path)
            throws FileNotFoundException {
        InputStream stream = classLoader.getResourceAsStream(path);
        if (stream == null)
            throw new FileNotFoundException(path);
        return stream;
    }

    private Expression stripConvertExpressions(Expression expression) {
        while (expression.getExpressionType() == ExpressionType.Convert) {
            expression = ((UnaryExpression) expression).getFirst();
        }
        return expression;
    }
}
