export function computeGamma(beta) {
    return 1 / Math.sqrt(1 - beta * beta);
}

export function computeDopplerFactor(beta, cosTheta) {
    const gamma = computeGamma(beta);
    const denominator = 1 - beta * cosTheta;
    return gamma / denominator;
}