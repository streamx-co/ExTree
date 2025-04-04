package co.streamx.fluent.extree.expression;

import io.github.dmlloyd.classfile.Signature;
import java.util.List;

interface ExpressionResolver {
    Expression getResult();

    void setResult(Expression result);

    void setStatements(List<Expression> statements);

    List<Expression> getStatements();

    void setLocals(List<Expression> locals);

    List<Expression> getLocals();

    ClassLoader getLoader();

    Class<?> getClass(Signature t);

    Class<?> getType();
}
