package concurrentcube;

public class CubeSquare {
    private final int face;
    private final int i;
    private final int j;

    public int getFace() {
		return face;
	}

	public int getI() {
		return i;
	}

	public int getJ() {
		return j;
	}

	public CubeSquare(int face, int i, int j) {
        this.face = face;
        this.i = i;
        this.j = j;
    }
}
