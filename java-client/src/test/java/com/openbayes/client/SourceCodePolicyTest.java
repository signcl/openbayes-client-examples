package com.openbayes.client;

import com.openbayes.client.model.SourceCodePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceCodePolicyTest {

    @Test
    void splitsBucketAndPrefix() {
        SourceCodePolicy p = new SourceCodePolicy(
                "WFkMTSaBui2", "https://s3.openbayes.com", "ak", "sk", "demo-user/codes/WFkMTSaBui2");
        assertEquals("demo-user", p.bucket());
        assertEquals("codes/WFkMTSaBui2", p.keyPrefix());
    }

    @Test
    void toleratesLeadingSlash() {
        assertArrayEquals(new String[] {"bkt", "a/b"}, SourceCodePolicy.splitPath("/bkt/a/b"));
    }

    @Test
    void handlesBucketOnly() {
        assertArrayEquals(new String[] {"bkt", ""}, SourceCodePolicy.splitPath("bkt"));
    }
}
