package net.shamansoft.cookbook;

import net.shamansoft.cookbook.config.TestFirebaseConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestFirebaseConfig.class)
class CookbookApplicationTests {

    @Test
    void contextLoads() {
    }

}
