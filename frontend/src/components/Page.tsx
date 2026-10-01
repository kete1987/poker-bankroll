import { Container, Stack, Title } from '@mantine/core';
import { useEffect, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

interface PageProps {
  title: string;
  children?: ReactNode;
}

/** Frame of a page: its heading, which is also the title of the browser tab. */
export function Page({ title, children }: PageProps) {
  const { t } = useTranslation();
  const appName = t('app.name');

  useEffect(() => {
    document.title = `${title} · ${appName}`;
    return () => {
      document.title = appName;
    };
  }, [title, appName]);

  return (
    <Container size="xl" px={0}>
      <Stack gap="md">
        <Title order={2}>{title}</Title>
        {children}
      </Stack>
    </Container>
  );
}
