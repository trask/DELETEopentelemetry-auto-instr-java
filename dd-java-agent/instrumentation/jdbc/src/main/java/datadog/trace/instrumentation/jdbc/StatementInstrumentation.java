package datadog.trace.instrumentation.jdbc;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.jdbc.JDBCDecorator.DECORATE;
import static datadog.trace.instrumentation.jdbc.JDBCUtils.connectionFromStatement;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class StatementInstrumentation extends Instrumenter.Default {

  public StatementInstrumentation() {
    super("jdbc");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("java.sql.Statement")));
  }

  @Override
  public String[] helperClassNames() {
    final List<String> helpers = new ArrayList<>(JDBCConnectionUrlParser.values().length + 9);

    helpers.add(packageName + ".DBInfo");
    helpers.add(packageName + ".DBInfo$Builder");
    helpers.add(packageName + ".JDBCUtils");
    helpers.add(packageName + ".JDBCMaps");
    helpers.add(packageName + ".JDBCConnectionUrlParser");

    helpers.add("datadog.trace.agent.decorator.BaseDecorator");
    helpers.add("datadog.trace.agent.decorator.ClientDecorator");
    helpers.add("datadog.trace.agent.decorator.DatabaseClientDecorator");
    helpers.add(packageName + ".JDBCDecorator");

    for (final JDBCConnectionUrlParser parser : JDBCConnectionUrlParser.values()) {
      helpers.add(parser.getClass().getName());
    }
    return helpers.toArray(new String[0]);
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        nameStartsWith("execute").and(takesArgument(0, String.class)).and(isPublic()),
        StatementAdvice.class.getName());
  }

  public static class StatementAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope onEnter(
        @Advice.Argument(0) final String sql, @Advice.This final Statement statement) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(Statement.class);
      if (callDepth > 0) {
        return null;
      }

      final Connection connection = connectionFromStatement(statement);
      if (connection == null) {
        return null;
      }

      final AgentSpan span = startSpan(DECORATE);
      DECORATE.onConnection(span, connection);
      DECORATE.onStatement(span, sql);
      return activateSpan(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
      if (scope != null) {
        DECORATE.onError(scope, throwable);
        scope.span().finish();
        scope.close();
        CallDepthThreadLocalMap.reset(Statement.class);
      }
    }
  }
}
