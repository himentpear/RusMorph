package org.namchieh.rusmorph.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPolicyTest {
    @Test fun production_rejects_http_and_malformed_urls() {
        assertFalse(EndpointPolicy.isAllowed("http://10.0.2.2:8787", allowCleartext = false))
        assertFalse(EndpointPolicy.isAllowed("not a url", allowCleartext = false))
        assertFalse(EndpointPolicy.isAllowed("https:///missing-host", allowCleartext = false))
    }

    @Test fun production_accepts_only_valid_https() {
        assertTrue(EndpointPolicy.isAllowed("https://api.namchieh.org/", allowCleartext = false))
        assertTrue(EndpointPolicy.isAllowed("http://10.0.2.2:8787", allowCleartext = true))
    }
}
