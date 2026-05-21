import { FileTextOutlined, MessageOutlined, SettingOutlined } from "@ant-design/icons";
import { Badge, ConfigProvider, Layout, Menu, Space, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import "./styles.css";

const { Header, Content, Sider } = Layout;
const { Title, Text } = Typography;

type PageKey = "documents" | "chat" | "settings";

type DocumentRow = {
  key: string;
  name: string;
  type: string;
  status: string;
  chunks: number;
};

const documents: DocumentRow[] = [
  {
    key: "spec",
    name: "2026-05-21-rag-system-design.md",
    type: "Markdown",
    status: "READY",
    chunks: 0
  }
];

function DocumentsPage() {
  const columns: ColumnsType<DocumentRow> = [
    { title: "文件名", dataIndex: "name", key: "name" },
    { title: "格式", dataIndex: "type", key: "type", width: 120 },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      width: 120,
      render: (status: string) => <Tag color="green">{status}</Tag>
    },
    { title: "切片数", dataIndex: "chunks", key: "chunks", width: 120 }
  ];

  return (
    <section className="workspace-panel" aria-labelledby="documents-title">
      <div className="panel-heading">
        <div>
          <Title id="documents-title" level={2}>
            文档
          </Title>
          <Text type="secondary">MVP 文档处理入口</Text>
        </div>
        <Badge status="processing" text="待接入上传流程" />
      </div>
      <Table columns={columns} dataSource={documents} pagination={false} />
    </section>
  );
}

function ChatPage() {
  return (
    <section className="workspace-panel" aria-labelledby="chat-title">
      <div className="panel-heading">
        <div>
          <Title id="chat-title" level={2}>
            对话
          </Title>
          <Text type="secondary">RAG 问答工作区</Text>
        </div>
        <Tag color="blue">基础问答</Tag>
      </div>
      <div className="chat-surface">
        <div className="message message-user">公司的报销流程是什么？</div>
        <div className="message message-assistant">上传文档并完成索引后，这里会展示带引用来源的答案。</div>
      </div>
    </section>
  );
}

function SettingsPage() {
  return (
    <section className="workspace-panel" aria-labelledby="settings-title">
      <div className="panel-heading">
        <div>
          <Title id="settings-title" level={2}>
            配置
          </Title>
          <Text type="secondary">当前 MVP 默认参数</Text>
        </div>
      </div>
      <Space size="large" wrap>
        <Statistic title="向量召回 Top-K" value={8} />
        <Statistic title="切片长度" value={500} />
        <Statistic title="记忆窗口" value={5} />
      </Space>
    </section>
  );
}

function App() {
  const [page, setPage] = useState<PageKey>("documents");

  const content = useMemo(() => {
    if (page === "chat") {
      return <ChatPage />;
    }

    if (page === "settings") {
      return <SettingsPage />;
    }

    return <DocumentsPage />;
  }, [page]);

  return (
    <ConfigProvider
      theme={{
        token: {
          borderRadius: 8,
          colorPrimary: "#2364aa",
          fontFamily: "Segoe UI, Microsoft YaHei, sans-serif"
        }
      }}
    >
      <Layout className="app-shell">
        <Sider className="app-sider" width={232}>
          <div className="brand-mark">
            <div className="brand-dot" />
            <span>Enterprise RAG</span>
          </div>
          <Menu
            mode="inline"
            selectedKeys={[page]}
            onClick={({ key }) => setPage(key as PageKey)}
            items={[
              { key: "documents", icon: <FileTextOutlined />, label: "文档" },
              { key: "chat", icon: <MessageOutlined />, label: "对话" },
              { key: "settings", icon: <SettingOutlined />, label: "配置" }
            ]}
          />
        </Sider>
        <Layout>
          <Header className="app-header">
            <Title level={1}>Enterprise RAG Knowledge Base Agent</Title>
            <Text type="secondary">Phase 1 Skeleton</Text>
          </Header>
          <Content className="app-content">{content}</Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
}

export default App;
