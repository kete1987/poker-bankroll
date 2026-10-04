import { Alert, Loader, Tabs } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';

import { useCatalog } from '../api/catalog';
import { useRooms } from '../api/rooms';
import { useTemplates } from '../api/templates';
import { useVariants } from '../api/variants';
import { Page } from '../components/Page';
import { RoomsSettings } from '../settings/RoomsSettings';
import { TemplatesSettings } from '../settings/TemplatesSettings';
import { VariantsSettings } from '../settings/VariantsSettings';

const TABS = ['rooms', 'variants', 'templates'] as const;
type Tab = (typeof TABS)[number];

function isTab(value: string | null): value is Tab {
  return (TABS as readonly (string | null)[]).includes(value);
}

/** Settings: the rooms, the variants and the templates the rest of the app works with. */
export function SettingsPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const requested = params.get('tab');
  const tab: Tab = isTab(requested) ? requested : 'rooms';

  const rooms = useRooms();
  const variants = useVariants();
  const catalog = useCatalog();
  const templates = useTemplates();

  return (
    <Page title={t('nav.settings')}>
      {[rooms, variants, catalog, templates].some((query) => query.isError) ? (
        <Alert color="red">{t('games.loadError')}</Alert>
      ) : !rooms.data || !variants.data || !catalog.data || !templates.data ? (
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
            <Tabs.Tab value="templates">{t('settings.tabs.templates')}</Tabs.Tab>
          </Tabs.List>
          <Tabs.Panel value="rooms">
            <RoomsSettings rooms={rooms.data} currencies={catalog.data.currencies} />
          </Tabs.Panel>
          <Tabs.Panel value="variants">
            <VariantsSettings variants={variants.data} />
          </Tabs.Panel>
          <Tabs.Panel value="templates">
            <TemplatesSettings
              templates={templates.data}
              rooms={rooms.data}
              variants={variants.data}
            />
          </Tabs.Panel>
        </Tabs>
      )}
    </Page>
  );
}
