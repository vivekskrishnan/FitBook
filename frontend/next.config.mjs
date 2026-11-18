/** @type {import('next').NextConfig} */
const nextConfig = {
  // Self-contained server output for the Docker image (the Dockerfile sets NEXT_STANDALONE=true).
  // Off by default so `next start` works locally.
  output: process.env.NEXT_STANDALONE === 'true' ? 'standalone' : undefined,
};

export default nextConfig;
