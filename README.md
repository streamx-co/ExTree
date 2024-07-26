![Java Version](https://img.shields.io/badge/java-%3E%3D%208-success) [![Maven Central](https://img.shields.io/maven-central/v/co.streamx.fluent/ex-tree?label=maven%20central)](https://central.sonatype.com/artifact/co.streamx.fluent/ex-tree)

# ExTree

This library enables Java Lambdas to be represented as objects in the form of expression trees at runtime:

```java
void method(SerializablePredicate<Customer> p) {
  LambdaExpression<Predicate<Customer>> parsed = LambdaExpression.parse(p);
  //Use parsed Expression Tree...
}
```

making it possible to create type-safe fluent interfaces, i.e. instead of:

```java
Customer obj = ...
obj.property("name").eq("John")
```

one can write

```java
method<Customer>(obj -> obj.getName() == "John")
```

in type-safe, refactoring friendly manner. And then the library developer will be able to parse the produced Lambda to the corresponding Expression Tree for analysis.

---

ExTree serves as an infrastructure for 2 other projects, allowing you to write SQL or Mongo queries using Java:

- [FluentJPA](https://github.com/streamx-co/FluentJPA)
- [FluentMongo](https://github.com/streamx-co/FluentMongo)

---

It's important to say that the library is generic and is not limited to the projects above.

### How to write fluent interface with ExTree?

- Suppose you want to reference some class property

```java
public class Fluent<T> {

	// this interface is required to make the lambda Serializable, which removes a need for 
	// jdk.internal.lambda.dumpProxyClasses system property. See below.
	public static interface Property<T, R> extends Function<T, R>, Serializable {
	}

	public Fluent<T> property(Property<T, ?> propertyRef) {
		LambdaExpression<Function<T, ?>> parsed = LambdaExpression
				.parse(propertyRef);
		Expression body = parsed.getBody();
		Expression methodCall = body;
		
		// remove casts
		while (methodCall instanceof UnaryExpression)
			methodCall = ((UnaryExpression) methodCall).getFirst();

		// checks are omitted for brevity
		Member member = ((MemberExpression) ((InvocationExpression) methodCall)
				.getTarget()).getMember();
		
		// use member
		...
		
		return this;
	}
}
```

- Now your users will be able to write

```java
Fluent<Customer> f = new Fluent<Customer>();
f.property(Customer::getName);
```

### Build instructions (*NIX): 
>  - mkdir $HOME/lambda
>  - mvn clean install

# License

ExTree is distributed under the terms of the LGPL license.

### Release instructions

- export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64
- mvn release:clean release:prepare -P release
- mvn release:perform -P release | mvn repository:bundle-create -P release
