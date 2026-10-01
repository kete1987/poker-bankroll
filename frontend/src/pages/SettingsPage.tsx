import { Alert, Loader, Tabs } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';

import { useCatalog } from '../api/catalog';
import { useRooms } from '../api/rooms';
import { useVariants } from '../api/variants';
import { Page } from '../components/Page';
import { RoomsSettings } from '../settings/RoomsSettings';
import { VariantsSettings } from '../settings/VariantsSettings';

const TABS = ['rooms', 'variants'] as const;
type Tab = (typeof TABS)[number];

/** Settings: the rooms and the variants the rest of the app works with. */
export function SettingsPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const tab: Tab = params.get('tab') === 'variants' ? 'variants' : 'rooms';

  const rooms = useRooms();
  const variants = useVariants();
  const catalog = useCatalog();

  return (
    <Page title={t('nav.settings')}>
      {[rooms, variants, catalog].some((query) => query.isError) ? (
        <Alert color="red">{t('games.loadError')}</Alert>
      ) : !rooms.data || !variants.data || !catalog.data ? (
        <Loader />
      ) : (
        <Tabs
          value={tab}
          // The tab is in the URL, so a reload stays where it was.
          onChange={(value) =>
            setParams(value === 'variants' ? { tab: 'variants' } : {}, { replace: true })
          }
          keepMounted={false}
        >
          <Tabs.List mb="md">
            <Tabs.Tab value="rooms">{t('settings.tabs.rooms')}</Tabs.Tab>
            <Tabs.Tab value="variants">{t('settings.tabs.variants')}</Tabs.Tab>
          </Tabs.List>
          <Tabs.Panel value="rooms">
            <RoomsSettings rooms={rooms.data} currencies={catalog.data.currencies} />
          </Tabs.Panel>
          <Tabs.Panel value="variants">
            <VariantsSettings variants={variants.data} />
          </Tabs.Panel>
        </Tabs>
      )}
    </Page>
  );
}
