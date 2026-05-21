import type { ComponentType } from 'react'

import Alibaba from '@lobehub/icons/es/Alibaba'
import Anthropic from '@lobehub/icons/es/Anthropic'
import DeepSeek from '@lobehub/icons/es/DeepSeek'
import Gemini from '@lobehub/icons/es/Gemini'
import Grok from '@lobehub/icons/es/Grok'
import Mistral from '@lobehub/icons/es/Mistral'
import OpenAI from '@lobehub/icons/es/OpenAI'

type IconProps = { size?: number; 'aria-label'?: string }
type IconComponent = ComponentType<IconProps>

const iconByProvider: Record<string, IconComponent> = {
  openai: OpenAI as unknown as IconComponent,
  anthropic: Anthropic as unknown as IconComponent,
  google: Gemini as unknown as IconComponent,
  deepseek: DeepSeek as unknown as IconComponent,
  mistral: Mistral as unknown as IconComponent,
  alibaba: Alibaba as unknown as IconComponent,
  xai: Grok as unknown as IconComponent,
}

export function ProviderIcon({ provider, size = 14 }: { provider: string; size?: number }) {
  const Icon = iconByProvider[provider.toLowerCase()]
  if (!Icon) return null
  return <Icon size={size} aria-label={provider} />
}
