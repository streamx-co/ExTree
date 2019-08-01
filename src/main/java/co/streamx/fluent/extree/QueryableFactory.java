package co.streamx.fluent.extree;

import co.streamx.fluent.extree.expression.*;

/*public*/ interface QueryableFactory {
	<S> Queryable<S> createQueryable(Class<S> type, Expression e);
}
