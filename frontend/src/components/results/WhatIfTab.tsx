import { WhatIfPanel } from '../WhatIfPanel'
import type { RepositoryAnalysisCostEstimateResponse } from '../../types/api'
import type { CostMode } from '../../utils/formatters'
import type { PricingMap } from '../../utils/whatIfCost'

export interface WhatIfTabProps {
  estimates: RepositoryAnalysisCostEstimateResponse[]
  selectedMode: CostMode
  pricingMap: PricingMap
}

export function WhatIfTab({ estimates, selectedMode, pricingMap }: WhatIfTabProps) {
  return <WhatIfPanel estimates={estimates} selectedMode={selectedMode} pricingMap={pricingMap} />
}
