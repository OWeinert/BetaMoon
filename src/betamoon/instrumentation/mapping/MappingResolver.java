package betamoon.instrumentation.mapping;

import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.MethodRef;

/** Resolves canonical named symbols into a runtime namespace. */
public interface MappingResolver {
    String resolveClass(ClassRef classRef, RuntimeNamespace namespace);

    String resolveDescriptor(String namedDescriptor, RuntimeNamespace namespace);

    ResolvedMethod resolveMethod(MethodRef method, RuntimeNamespace namespace);

    ResolvedField resolveField(FieldRef field, RuntimeNamespace namespace);
}
