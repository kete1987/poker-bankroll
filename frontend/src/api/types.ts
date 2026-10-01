import type { components } from './schema';

/**
 * Shapes of the API requests and responses. `schema.d.ts` is generated from `openapi.json`
 * (`npm run api:types`); never edit it by hand. Add an alias here when a new shape is used.
 */
type Schemas = components['schemas'];

export type Catalog = Schemas['Catalog'];
export type Currency = Schemas['CurrencyResponse'];
export type GameType = Schemas['GameResponse']['gameType'];
export type Modality = Schemas['GameResponse']['modality'];
export type GameStatus = Schemas['GameResponse']['status'];

export type Room = Schemas['RoomResponse'];
export type RoomRequest = Schemas['RoomRequest'];

export type Variant = Schemas['VariantResponse'];
export type VariantCreateRequest = Schemas['VariantCreateRequest'];
export type VariantUpdateRequest = Schemas['VariantUpdateRequest'];

export type Game = Schemas['GameResponse'];
export type GameRequest = Schemas['GameRequest'];
export type FinishGameRequest = Schemas['FinishGameRequest'];
export type RebuyRequest = Schemas['RebuyRequest'];
export type GamePage = Schemas['PageResponseGameResponse'];

export type StatsFigures = Schemas['StatsFigures'];
export type StatsSummary = Schemas['StatsSummaryResponse'];
export type StatsGroups = Schemas['StatsGroupsResponse'];
export type GroupBy = StatsGroups['groupBy'];

export type MovementType = Schemas['MovementResponse']['type'];
export type Movement = Schemas['MovementResponse'];
export type MovementRequest = Schemas['MovementRequest'];
export type MovementPage = Schemas['PageResponseMovementResponse'];
export type BankrollSummary = Schemas['BankrollSummaryResponse'];
export type BankrollFigures = Schemas['BankrollFigures'];
