package bsh;

import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import bsh.util.ReferenceCache;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import org.hamcrest.Matchers;
import org.junit.Rule;


@RunWith(FilteredTestRunner.class)
public class ReferenceCacheTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void soft_reference_key() throws Exception {
        ReferenceCache<String,String> cache = new ReferenceCache<String,String>( val -> (val + "_value") );

        assertThat(cache.get("foo"), Matchers.equalTo("foo_value") );
        assertThat(cache.size(), equalTo(1));
        System.gc();
    }
}

