package dev.terravista.world;

/** 通过维度注入桥接世界身份访问。 */
public interface WorldIdentityProvider {
    WorldIdentity terraVistaIdentity();
}
