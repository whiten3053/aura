/*
 * Copyright (C) 2012 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.http;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.auraframework.Aura;
import org.auraframework.def.ApplicationDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.impl.system.DefDescriptorImpl;
import org.auraframework.system.AuraContext;
import org.auraframework.system.AuraContext.Mode;
import org.auraframework.test.AuraTestCase;
import org.auraframework.test.DummyHttpServletRequest;
import org.auraframework.test.DummyHttpServletResponse;

/**
 * Simple (non-integration) test case for {@link AuraResourceServlet}, most
 * useful for exercising hard-to-reach error conditions.
 * 
 * I would like this test to be in the "aura" module (vice "aura-impl"), but the
 * configuration there isn't friendly to getting a context service, and I think
 * changing that may impact other tests, so I'm leaving it at least for now.
 */
public class AuraResourceServletTest extends AuraTestCase {

    public static class SimulatedErrorException extends RuntimeException {
        private static final long serialVersionUID = 411181168049748986L;
    }

    public AuraResourceServletTest() {
        super(AuraResourceServletTest.class.getName());
    }

    public void testWriteManifestNoAccessError() throws Exception {
        // Start a context to fetch manifests; the other details don't matter
        // much 'cause we'll error out. Then try to fetch one, with that error:
        Aura.getContextService().startContext(AuraContext.Mode.UTEST, AuraContext.Format.MANIFEST,
                AuraContext.Access.PUBLIC);

        HttpServletRequest request = new DummyHttpServletRequest() {
            @Override
            // This is the method that's going to cause the simulated failure.
            public String getHeader(String name) {
                if ("user-agent".equals(name)) {
                    throw new SimulatedErrorException();
                }
                return "";
            }
        };
        // Careful. Resin apparently has no getStatus().
        DummyHttpServletResponse response = new DummyHttpServletResponse() {
            int status = -1;

            @Override
            public void setStatus(int status) {
                this.status = status;
            }

            @Override
            public int getStatus() {
                return status;
            }
        };
        AuraResourceServlet servlet = new AuraResourceServlet();
        servlet.doGet(request, response);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    }

    public void testAddAppManifestCookie() throws Exception {
        Aura.getContextService().startContext(AuraContext.Mode.UTEST, AuraContext.Format.MANIFEST,
                AuraContext.Access.PUBLIC);

        DefDescriptor<ApplicationDef> nopreload = DefDescriptorImpl.getInstance("appCache:nopreload",
                                                                                ApplicationDef.class);
        Aura.getContextService().getCurrentContext().setApplicationDescriptor(nopreload);

        DummyHttpServletRequest request = new DummyHttpServletRequest();
        DummyHttpServletResponse response = new DummyHttpServletResponse() {
            Cookie cookie;

            @Override
            public void addCookie(Cookie cookie) {
                this.cookie = cookie;
            }

            @Override
            public Cookie getCookie(String name) {
                return cookie != null && cookie.getName().equals(name) ? cookie : null;
            }
        };
        ManifestUtil.checkManifestCookie(request, response);
        String expectedName = Mode.UTEST + "_" + nopreload.getNamespace() + "_" + nopreload.getName() + "_lm";
        Cookie cookie = response.getCookie(expectedName);
        assertEquals(expectedName, cookie.getName());
        //
        // Format of the cookie is now <n>:<time>
        //
        assertEquals(AuraBaseServlet.SHORT_EXPIRE_SECONDS, cookie.getMaxAge());
        assertTrue("Cookie should contain : but was:"+cookie.getValue(), cookie.getValue().contains(":"));
        String countStr = cookie.getValue().substring(0, cookie.getValue().indexOf(':'));
        String startTimeStr = cookie.getValue().substring(countStr.length() + 1);
        try {
            int count = Integer.parseInt(countStr);
            assertTrue("count should be between 1 & 5 was "+count, (count >= 0 && count < 6));
        } catch (NumberFormatException nfe) {
            fail("Invalid count of "+countStr);
        }
        try {
            long startTime = Long.parseLong(startTimeStr);
            assertTrue("Start time should be in the past", (startTime <= System.currentTimeMillis()));
        } catch (NumberFormatException nfe) {
            fail("Invalid start time of "+startTimeStr);
        }
    }
}
