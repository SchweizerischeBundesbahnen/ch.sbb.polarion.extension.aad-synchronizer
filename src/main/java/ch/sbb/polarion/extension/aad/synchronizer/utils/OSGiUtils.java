package ch.sbb.polarion.extension.aad.synchronizer.utils;

import com.polarion.core.util.logging.Logger;
import lombok.experimental.UtilityClass;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

@UtilityClass
public class OSGiUtils {
    private static final Logger logger = Logger.getLogger((Object) OSGiUtils.class);

    public static <T> T lookupOSGiService(Class<T> serviceInterface) {
        Bundle bundle = FrameworkUtil.getBundle(OSGiUtils.class);
        if (bundle == null) {
            logger.warn("Cannot get OSGi bundle for aad-synchronizer via classloader: " + OSGiUtils.class.getClassLoader());
            return null;
        }
        BundleContext bundleContext = bundle.getBundleContext();
        if (bundleContext == null) {
            logger.warn("Bundle context is not yet initialized for bundle: " + bundle.getSymbolicName() + ". Check Bundle-ActivationPolicy: lazy");
            return null;
        }
        ServiceReference<?> serviceReference = bundleContext.getServiceReference(serviceInterface);
        if (serviceReference != null) {
            return (T) bundleContext.getService(serviceReference);
        } else {
            return null;
        }
    }

}
