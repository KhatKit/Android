package heizige.kk.khatkit.bridge.impl;

interface IKhatKitUserService {
    String exec(in String[] command);
    void destroy();
}
