import { Alert, Loader, Tabs } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';

import { useCatalog } from '../api/catalog';
import { useRooms } from '../api/rooms';
import { useTags } from '../api/tags';
import { useTemplates } from '../api/templates';
import { useVariants } from '../api/variants';
import { Page } from '../components/Page';
import { CurrenciesSettings } from '../settings/CurrenciesSettings';
import { RoomsSettings } from '../settings/RoomsSettings';
import { TagsSettings } from '../settings/TagsSettings';
import { TemplatesSettings } from '../settings/TemplatesSettings';
import { VariantsSettings } from '../settings/VariantsSettings';

const TABS = ['rooms', 'variants', 'tags', 'templates', 'currencies'] as const;
type Tab = (typeof TABS)[number];

/**
 * Settings: the rooms, the variants, the tags and the templates the rest of the app works with,
 * and the currencies (base currency and exchange rates).
 */
export function SettingsPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const tab: Tab = TABS.find((value) => value === params.get('tab')) ?? 'rooms';

  const rooms = useRooms();
  const variants = useVariants();
  const catalog = useCatalog();
  const tags = useTags();
  const templates = useTemplates();

  return (
    <Page title={t('nav.settings')}>
      {[rooms, variants, catalog, tags, templates].some((query) => query.isError) ? (
        <Alert color="red">{t('games.loadError')}</Alert>
      ) : !rooms.data || !variants.data || !catalog.data || !tags.data || !templates.data ? (
        <Loader />
      ) : (
        <Tabs
          value={tab}
          // The tab is in the URL, so a reload stays where it was.
          onChange={(value) =>
            setParams(value && value !== 'rooms' ? { tab: value } : {}, { replace: true })
          }
          keepMounted={false}
        >
          <Tabs.List mb="md">
            <Tabs.Tab value="rooms">{t('settings.tabs.rooms')}</Tabs.Tab>
            <Tabs.Tab value="variants">{t('settings.tabs.variants')}</Tabs.Tab>
            <Tabs.Tab value="tags">{t('settings.tabs.tags')}</Tabs.Tab>
            <Tabs.Tab value="templates">{t('settings.tabs.templates')}</Tabs.Tab>
            <Tabs.Tab value="currencies">{t('settings.tabs.currencies')}</Tabs.Tab>
          </Tabs.List>
          <Tabs.Panel value="rooms">
            <RoomsSettings rooms={rooms.data} currencies={catalog.data.currencies} />
          </Tabs.Panel>
          <Tabs.Panel value="variants">
            <VariantsSettings variants={variants.data} />
          </Tabs.Panel>
          <Tabs.Panel value="tags">
            <TagsSettings tags={tags.data} />
          </Tabs.Panel>
          <Tabs.Panel value="templates">
            <TemplatesSettings
              templates={templates.data}
              rooms={rooms.data}
              variants={variants.data}
            />
          </Tabs.Panel>
          <Tabs.Panel value="currencies">
            <CurrenciesSettings currencies={catalog.data.currencies} />
          </Tabs.Panel>
        </Tabs>
      )}
    </Page>
  );
}
