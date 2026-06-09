package ch.sbb.polarion.extension.aad.synchronizer.utils;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class OSGiUtilsTest {

    @Test
    void returnsNullWhenBundleCannotBeResolved() {
        try (MockedStatic<FrameworkUtil> frameworkUtil = mockStatic(FrameworkUtil.class)) {
            frameworkUtil.when(() -> FrameworkUtil.getBundle(OSGiUtils.class)).thenReturn(null);

            assertThat(OSGiUtils.lookupOSGiService(Runnable.class)).isNull();
        }
    }

    @Test
    void returnsNullWhenBundleContextIsNotInitialized() {
        Bundle bundle = mock(Bundle.class);
        when(bundle.getBundleContext()).thenReturn(null);

        try (MockedStatic<FrameworkUtil> frameworkUtil = mockStatic(FrameworkUtil.class)) {
            frameworkUtil.when(() -> FrameworkUtil.getBundle(OSGiUtils.class)).thenReturn(bundle);

            assertThat(OSGiUtils.lookupOSGiService(Runnable.class)).isNull();
        }
    }

    @Test
    void returnsNullWhenServiceReferenceIsMissing() {
        Bundle bundle = mock(Bundle.class);
        BundleContext bundleContext = mock(BundleContext.class);
        when(bundle.getBundleContext()).thenReturn(bundleContext);
        doReturn(null).when(bundleContext).getServiceReference(Runnable.class);

        try (MockedStatic<FrameworkUtil> frameworkUtil = mockStatic(FrameworkUtil.class)) {
            frameworkUtil.when(() -> FrameworkUtil.getBundle(OSGiUtils.class)).thenReturn(bundle);

            assertThat(OSGiUtils.lookupOSGiService(Runnable.class)).isNull();
        }
    }

    @Test
    void returnsServiceInstanceWhenAvailable() {
        Bundle bundle = mock(Bundle.class);
        BundleContext bundleContext = mock(BundleContext.class);
        ServiceReference<?> serviceReference = mock(ServiceReference.class);
        Runnable service = mock(Runnable.class);

        when(bundle.getBundleContext()).thenReturn(bundleContext);
        doReturn(serviceReference).when(bundleContext).getServiceReference(Runnable.class);
        doReturn(service).when(bundleContext).getService(serviceReference);

        try (MockedStatic<FrameworkUtil> frameworkUtil = mockStatic(FrameworkUtil.class)) {
            frameworkUtil.when(() -> FrameworkUtil.getBundle(OSGiUtils.class)).thenReturn(bundle);

            assertThat(OSGiUtils.lookupOSGiService(Runnable.class)).isSameAs(service);
        }
    }
}
