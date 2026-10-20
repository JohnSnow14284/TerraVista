void setupQuad(out QuadData quad, const in Quad rawQuad, uvec2 sPos, bool generateAttributes) {
    uint lodLevel = getLoDLevel(sPos);
    float lodScale = 1<<lodLevel;
    ivec3 baseSection = (getLoDPosition(sPos)<<lodLevel) - baseSectionPos;

    uint face = extractFace(rawQuad);
    uint modelId = extractStateId(rawQuad);
    BlockModel model = modelData[modelId];
    uint faceData = model.faceData[face];
    bool isFluid = modelIsFluid(model);
    bool hasSurfaceHeight = !isFluid && (extractFluidCornerHeights(rawQuad) & 2048u) != 0u;
    ivec2 quadSize = isFluid || hasSurfaceHeight ? ivec2(1) : extractSize(rawQuad);

    if (generateAttributes) {
        quad.attributeData.x = makeQuadFlags(faceData, modelId, quadSize, model, face);
        quad.attributeData.yzw = makeRemainingAttributes(model, rawQuad, lodLevel, face);
    }

    vec4 textureFaceSize = getFaceSize(faceData);
    vec4 geometryFaceSize = textureFaceSize;

    // 纹理范围保持在流体烘焙像素内。
    // 几何使用精确单元边界。
    // 按邻域计算的四角高度调整流体顶点。
    if (isFluid) {
        geometryFaceSize = vec4(0.0, 1.0, 0.0, 1.0);
    }

    vec3 quadStart = extractPos(rawQuad);
    quad.cellBottom = quadStart.y * lodScale + float((baseSection<<5).y);
    quad.surfaceScale = 1.0;
    quad.surfaceOffset = 0.0;
    if (hasSurfaceHeight) {
        float height = float(extractFluidCornerHeights(rawQuad) & 511u) / 256.0;
        float thickness = height;
        // 表层冰盖保留一格厚度及实际顶面位置。
        if ((extractFluidCornerHeights(rawQuad) & 512u) != 0u) {
            thickness = min(height, 1.0 / lodScale);
            quad.surfaceOffset = (height - thickness) * lodScale;
        }
        quad.surfaceScale = thickness / max(1.0 - extractFaceIndentation(model.faceData[1]), 1.0 / 64.0);
    }
    float depthOffset = isFluid ? 0.0 : extractFaceIndentation(faceData);
    quadStart += swizzelDataAxis(face>>1, vec3(geometryFaceSize.xz, mix(depthOffset, 1-depthOffset, float(face&1u))));

    quad.lodScale = lodScale;
    quad.axis = face>>1;
    quad.basePoint = (quadStart*lodScale)+vec3(baseSection<<5);
    quad.quadSizeAddin = geometryFaceSize.yw + quadSize - 1;
    quad.uvSizeAddin = textureFaceSize.yw + quadSize - 1;
    quad.uvCorner = textureFaceSize.xz;
    quad.face = face;
    quad.fluidCornerHeights = isFluid ? extractFluidCornerHeights(rawQuad) : uint(-1);
    quad.fluidDatumOffset = 0.0;
    quad.coarseFluidTop = -1.0;
    quad.preciseFluid = false;
    if (isFluid && lodLevel > 0u) {
        uint height = quad.fluidCornerHeights & 511u;
        uint cap = quad.fluidCornerHeights >> 9;
        float surface = (cap == 6u ? 8.0 / 9.0 : float(cap + 1u) / 8.0) - 0.001;
        quad.preciseFluid = height != 0u;
        if (quad.preciseFluid) quad.coarseFluidTop = quad.cellBottom + float(height) / 256.0 * lodScale + surface - 1.0;
        quad.fluidCornerHeights = cap * 585u;
    }
    if (isFluid && lodLevel > 0u && !quad.preciseFluid && (model.flagsA & 256u) != 0u) {
        float topY = (quadStart.y + (face == 1u ? 0.0 : 1.0)) * lodScale
                + float((getLoDPosition(sPos).y << lodLevel) << 5);
        if (seaSurfaceY > topY - lodScale && seaSurfaceY <= topY) {
            uint cap = quad.fluidCornerHeights & 7u;
            float surface = (cap == 6u ? 8.0 / 9.0 : float(cap + 1u) / 8.0) - 0.001;
            quad.fluidDatumOffset = seaSurfaceY - (topY + surface - 1.0);
        }
    }
}

vec4 getQuadCornerPos(in QuadData quad, uint cornerId) {
    uvec2 cornerBits = uvec2((cornerId>>1)&1u, cornerId&1u);
    vec2 cornerMask = vec2(cornerBits)*quad.lodScale;
    vec3 point = quad.basePoint + swizzelDataAxis(quad.axis,vec3(quad.quadSizeAddin*cornerMask,0));

    if (quad.fluidCornerHeights != uint(-1)) {
        uint heightIndex = 0u;
        bool applyHeight = false;
        if (quad.face == 1u) {
            // 顶面的平面轴对应世界 X 与 Z。
            heightIndex = (cornerBits.x<<1)|cornerBits.y;
            applyHeight = true;
        } else if (quad.axis == 1u && cornerBits.y == 1u) {
            // 南北面的水平轴为 X，Z 表示面侧。
            heightIndex = (cornerBits.x<<1)|(quad.face&1u);
            applyHeight = true;
        } else if (quad.axis == 2u && cornerBits.x == 1u) {
            // 东西面的水平轴为 Z，X 表示面侧。
            heightIndex = ((quad.face&1u)<<1)|cornerBits.y;
            applyHeight = true;
        }

        if (applyHeight) {
            uint encoded = (quad.fluidCornerHeights>>(heightIndex*3u))&7u;
            float height = (encoded == 6u ? 8.0 / 9.0 : float(encoded + 1u)/8.0) - 0.001;
            point.y = quad.preciseFluid ? quad.coarseFluidTop : point.y + height - 1.0 + quad.fluidDatumOffset;
        }
    }
    point.y = quad.cellBottom + quad.surfaceOffset + (point.y - quad.cellBottom) * quad.surfaceScale;

    if (worldCurveData.x != 0.0) point = curvePoint(point - cameraSubPos, worldCurveData) + cameraSubPos;
    vec4 pos = MVP * vec4(point, 1.0f);
    pos.xy += taaOffset*pos.w;
    return pos;
}

vec2 getCornerUV(const in QuadData quad, uint cornerId) {
    return quad.uvCorner + quad.uvSizeAddin*vec2((cornerId>>1)&1u, cornerId&1u);
}

float computeDirectionalFaceTint(bool isShaded, uint face) {
    // 按面方向应用着色系数。
    if (isShaded) {

        if ((face>>1) == 1) {// 处理南北面。
            return Z_AXIS_FACE_TINT;
        } else if ((face>>1) == 2) {// 处理东西面。
            return X_AXIS_FACE_TINT;
        } else if (face == 1) {// 处理顶面。
            return UP_FACE_TINT;
        }
        // 处理底面。
        return DOWN_FACE_TINT;
    } else {
        return NO_SHADE_FACE_TINT;
    }
}
