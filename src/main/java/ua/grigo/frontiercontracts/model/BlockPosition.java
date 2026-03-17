package ua.grigo.frontiercontracts.model;

public record BlockPosition(int x, int y, int z) {
    public String key(String worldName) {
        return worldName + ":" + x + ":" + y + ":" + z;
    }
}
