package net.thanachot.lemmecuttrees.core;

public record GridPos(int x, int y, int z) implements Comparable<GridPos> {
    public GridPos offset(int dx, int dy, int dz) {
        return new GridPos(x + dx, y + dy, z + dz);
    }

    public long squaredDistance(GridPos other) {
        long dx = x - (long) other.x;
        long dy = y - (long) other.y;
        long dz = z - (long) other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public int compareTo(GridPos other) {
        int result = Integer.compare(x, other.x);
        if (result == 0) result = Integer.compare(y, other.y);
        if (result == 0) result = Integer.compare(z, other.z);
        return result;
    }
}
