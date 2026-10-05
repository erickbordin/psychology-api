import { test } from '@playwright/test'

type Metadados = { convites: string[]; prefixoApi: string }

export function ambiente() {
  const { convites, prefixoApi } = test.info().config.metadata as Metadados
  return { convites, prefixoApi }
}
