package dev.terravista.storage;

import dev.terravista.world.WorldIdentity;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 按维度命名存储目录并识别先前的数据目录。 */
public final class WorldStorageIdentity {
    private WorldStorageIdentity() {}
    public static String directory(WorldIdentity identity) {
        var id = identity.key.identifier();
        return id.getNamespace().equals("minecraft") && switch (id.getPath()) {
            case "overworld", "the_nether", "the_end" -> true;
            default -> false;
        } ? id.getPath()
                : id.getNamespace() + "@" + id.getPath().replace("/", "%2F");
    }
    public static String previousDirectory(WorldIdentity identity) {
        String data = identity.biomeSeed + identity.key.toString();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes()), 0, 16);
        } catch (NoSuchAlgorithmException exception) { throw new RuntimeException(exception); }
    }
}
