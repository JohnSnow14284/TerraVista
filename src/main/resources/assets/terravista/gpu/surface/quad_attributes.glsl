uint makeQuadFlags(uint faceData, uint modelId, ivec2 quadSize, const in BlockModel model, uint face) {
    // 打包裁剪、mip、着色、面、宽高与模型编号位。
    uint flags = 0;

    flags |= modelId<<16;// 写入模型编号。
    flags |= (uint(quadSize.x-1)<<8)|(uint(quadSize.y-1)<<12);// 写入四边形尺寸。

    {// 写入裁剪标记。
        flags |= faceHasAlphaCuttout(faceData);
        flags |= uint(any(greaterThan(quadSize, ivec2(1)))) & faceHasAlphaCuttoutOverride(faceData);
    }

    flags |= faceTintState(faceData)<<2;
    flags |= face<<4;// 写入面方向。

    return flags;
}

uint packVec4(vec4 vec) {
    uvec4 vec_=uvec4(vec*255)<<uvec4(24,16,8,0);
    return vec_.x|vec_.y|vec_.z|vec_.w;
}

float computeDirectionalFaceTint(bool isShaded, uint face);

uvec3 makeRemainingAttributes(const in BlockModel model, const in Quad quad, uint lodLevel, uint face) {
    uvec3 attributes = uvec3(0);

    uint lighting = extractLightId(quad);

    // 应用模型颜色着色。
    uint tintColour = model.colourTint;

    if (modelHasBiomeLUT(model) && !usesBlendedTint(quad))
        tintColour = colourData[tintColour + extractBiomeId(quad)];

    bool isTranslucent = modelIsTranslucent(model);

    bool isShaded = modelIsShaded(model);
    bool hasAO = isShaded;

    vec4 tinting = getLighting(lighting);
    if (usesBlendedTint(quad)) {
        uint blended = colourData[65536u + extractBiomeId(quad)];
        if (face == 1u) tinting.rgb *= float(blended >> 24) / 255.0;
        if (modelHasBiomeLUT(model)) tintColour = blended | 0xff000000u;
    }

    uint conditionalTinting = 0;
    if (tintColour != uint(-1)) {
        conditionalTinting = tintColour;
    }

    uint addin = 0;
    if (!isTranslucent) {
        tinting.w = 0.0;
        // 编码面方向与 LOD 层级。
        uint encodedData = 0;
        encodedData |= face;
        encodedData |= (lodLevel<<3);
        encodedData |= uint(hasAO)<<6;
        addin = encodedData;
    }

    tinting.rgb *= computeDirectionalFaceTint(isShaded, face);

    attributes.x = packVec4(tinting);
    attributes.y = conditionalTinting;
    attributes.z = addin|(face<<8)|(((model.flagsA>>6)&3u)<<11);
    // 实体透明模型只绘制朝向观察者的表面。
    if (isTranslucent && (model.flagsA & (16u | 32u)) == 0u) attributes.z |= 1u << 13;

    return attributes;
}

