uint recordVisibility(uint sid) {
    uint current=frameId&0x7fffffffu;
    uint prior=atomicOr(visibilityData[sid],0u);
    uint old=prior&0x7fffffffu;
    if(old==current) return prior;
    uint value=current|(uint(old==((current-1u)&0x7fffffffu))<<31);
    atomicExchange(visibilityData[sid],value);
    return value;
}
