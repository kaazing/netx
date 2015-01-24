/*
 * Copyright 2014, Kaazing Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kaazing.netx.http.bridge.itest.internal;

import static java.lang.System.getProperties;
import static java.lang.System.getProperty;
import static java.lang.System.setProperties;
import static java.lang.System.setSecurityManager;
import static java.security.Policy.setPolicy;

import java.net.URL;
import java.security.Policy;
import java.util.Properties;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import sun.applet.AppletSecurity;
import sun.security.provider.PolicyFile;

public final class AppletRule implements TestRule {

    @Override
    public Statement apply(final Statement base, Description description) {
        
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                Policy oldPolicy = Policy.getPolicy();
                Properties oldProperties = getProperties();
                try {
                    URL policyFile = getClass().getResource("applet.policy");
                    Policy newPolicy = new PolicyFile(policyFile);

                    Properties newProperties = new Properties();

                    // Standard browser properties
                    newProperties.put("browser", "sun.applet.AppletViewer");
                    newProperties.put("browser.version", "1.06");
                    newProperties.put("browser.vendor", "Oracle Corporation");
                    newProperties.put("http.agent", "Java(tm) 2 SDK, Standard Edition v" + getProperty("java.version"));

                    // Define which packages can be extended by applets
                    newProperties.put("package.restrict.definition.java", "true");
                    newProperties.put("package.restrict.definition.sun", "true");

                    newProperties.put("java.version.applet", "true");
                    newProperties.put("java.vendor.applet", "true");
                    newProperties.put("java.vendor.url.applet", "true");
                    newProperties.put("java.class.version.applet", "true");
                    newProperties.put("os.name.applet", "true");
                    newProperties.put("os.version.applet", "true");
                    newProperties.put("os.arch.applet", "true");
                    newProperties.put("file.separator.applet", "true");
                    newProperties.put("path.separator.applet", "true");
                    newProperties.put("line.separator.applet", "true");

                    setProperties(newProperties);
                    setPolicy(newPolicy);
                    setSecurityManager(new AppletSecurity());

                    base.evaluate();
                }
                finally {
                    setPolicy(oldPolicy);
                    setProperties(oldProperties);
                }
            }

        };
    }

}
