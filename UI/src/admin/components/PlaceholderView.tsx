import { Card, CardContent, CardHeader, CardTitle } from './ui/card';

export function PlaceholderView({ title, message }: { title: string; message: string }) {
  return (
    <Card className="border border-gray-200 shadow-sm">
      <CardHeader className="bg-[#1E293B] text-white">
        <CardTitle>{title}</CardTitle>
      </CardHeader>
      <CardContent className="p-6">
        <p className="text-slate-600 py-8 text-center">{message}</p>
      </CardContent>
    </Card>
  );
}
