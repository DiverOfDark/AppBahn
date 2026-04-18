package eu.appbahn.operator.reconciler;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

/**
 * Resolves {@link DependentResource} instances from Spring when a matching bean exists, falling
 * back to JOSDK's default otherwise. Beans must be {@code @Scope("prototype")} — JOSDK expects
 * each workflow binding to own its own instance.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SpringDependentResourceFactory implements DependentResourceFactory {

    private final ApplicationContext applicationContext;

    public SpringDependentResourceFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public DependentResource createFrom(DependentResourceSpec spec, ControllerConfiguration config) {
        DependentResource bean = resolveBean(spec.getDependentResourceClass());
        if (bean != null) {
            configure(bean, spec, config);
            return bean;
        }
        return DependentResourceFactory.DEFAULT.createFrom(spec, config);
    }

    @Override
    public Class<?> associatedResourceType(DependentResourceSpec spec) {
        DependentResource bean = resolveBean(spec.getDependentResourceClass());
        if (bean != null) {
            return bean.resourceType();
        }
        return DependentResourceFactory.DEFAULT.associatedResourceType(spec);
    }

    private DependentResource resolveBean(Class<?> dependentResourceClass) {
        try {
            Object bean = applicationContext.getBean(dependentResourceClass);
            return bean instanceof DependentResource dr ? dr : null;
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }
}
