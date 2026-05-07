export async function getHealth(): Promise<{ status: string; service: string }> {
  const response = await fetch('/api/health')

  if (!response.ok) {
    throw new Error(`Health check failed with status ${response.status}`)
  }

  return response.json() as Promise<{ status: string; service: string }>
}
