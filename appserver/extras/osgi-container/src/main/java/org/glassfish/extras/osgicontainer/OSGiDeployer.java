/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.extras.osgicontainer;

import org.glassfish.api.deployment.Deployer;
import org.glassfish.api.deployment.DeploymentContext;
import org.glassfish.api.deployment.MetaData;
import org.glassfish.api.deployment.OpsParams;
import org.jvnet.hk2.annotations.Service;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleReference;
import org.osgi.service.packageadmin.PackageAdmin;

import java.io.File;

/**
 * OSGi deployer, takes care of loading and cleaning modules from the OSGi runtime.
 *
 * @author Jerome Dochez
 * @author Sanjeeb Sahoo
 */
@Service
public class OSGiDeployer implements Deployer<OSGiContainer, OSGiDeployedBundle> {

    private static final String BUNDLE_ID = "bundle.id";

    public OSGiDeployedBundle load(OSGiContainer container, DeploymentContext context) {
        return new OSGiDeployedBundle(getApplicationBundle(context, true));
    }

    public void unload(OSGiDeployedBundle appContainer, DeploymentContext context) {
    }

    public void clean(DeploymentContext context) {
        try {
            OpsParams params = context.getCommandParameters(OpsParams.class);
            // we should clean for both undeployment and the failed deployment
            if (params.origin.isUndeploy() || params.origin.isDeploy()) {
                Bundle bundle = getApplicationBundle(context);
                bundle.uninstall();
                getPA().refreshPackages(new Bundle[]{bundle});
                System.out.println("Uninstalled " + bundle);
            }
        } catch (BundleException e) {
            throw new RuntimeException(e);
        }
    }

    private PackageAdmin getPA() {
        final BundleContext context = getBundleContext();
        return (PackageAdmin) context.getService(context.getServiceReference(PackageAdmin.class.getName()));
    }

    public MetaData getMetaData() {
        return null;
    }

    public <V> V loadMetaData(Class<V> type, DeploymentContext context) {
        return null;
    }

    public boolean prepare(DeploymentContext context) {
        File file = context.getSourceDir();
        OpsParams params = context.getCommandParameters(OpsParams.class);
        if (params.origin.isDeploy()) {
            assert(file.isDirectory());
            installBundle(makeBundleLocation(file));
        }
        return true;
    }

    private Bundle installBundle(final String location) {
        try {
            Bundle bundle = getBundleContext().installBundle(location);
            System.out.println("Installed " + bundle + " from " + bundle.getLocation());
            return bundle;
        } catch (BundleException e) {
            throw new RuntimeException(e);
        }
    }

    private BundleContext getBundleContext() {
        return BundleReference.class.cast(getClass().getClassLoader()).getBundle().getBundleContext();
    }

    private Bundle getApplicationBundle(DeploymentContext context, boolean reinstallIfAbsent) {
        String location = makeBundleLocation(context.getSourceDir());
        for(Bundle b : getBundleContext().getBundles()) {
            if (location.equals(b.getLocation())) {
                return b;
            }
        }
        if (reinstallIfAbsent) {
            System.out.println("Bundle does not exist, so reinstalling from " + location);
            return installBundle(location);
        }
        throw new RuntimeException("Unable to determine bundle corresponding to application location " + context.getSourceDir());
    }

    private Bundle getApplicationBundle(DeploymentContext context) {
        return getApplicationBundle(context, false);
    }

    private String makeBundleLocation(File file) {
        return "reference:" + file.toURI();
    }

}
