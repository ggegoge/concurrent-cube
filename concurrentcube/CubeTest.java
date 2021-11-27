package concurrentcube;

import org.junit.Test;

public class CubeTest {
    @Test
    public void git() {
        
    }

    @Test
    public void chuj() {
        throw new AssertionError("chuj");
    }

    @Test
    public void cube1() {
        Cube c = new Cube(3, (x, y) -> { System.out.println("before"); }, (x, y) -> { System.out.println("after");} () -> {}, () -> {});
    }
}
