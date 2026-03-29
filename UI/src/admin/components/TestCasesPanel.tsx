import { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Button } from './ui/button';
import { Badge } from './ui/badge';
import { Plus } from 'lucide-react';
import { AddTestCaseModal } from './AddTestCaseModal';
import { getTestCasesForProblem } from '../services/api';
import { TestCaseResponse } from '../types/api';
import { toast } from 'sonner';

interface TestCasesPanelProps {
  problemId: number;
  problemTitle: string;
}

export function TestCasesPanel({ problemId, problemTitle }: TestCasesPanelProps) {
  const [testCases, setTestCases] = useState<TestCaseResponse[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [addModalOpen, setAddModalOpen] = useState(false);

  const loadTestCases = async () => {
    setIsLoading(true);
    try {
      const data = await getTestCasesForProblem(problemId);
      setTestCases(data);
    } catch (error) {
      console.error('Failed to load test cases:', error);
      toast.error('Failed to load test cases');
      setTestCases([]);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadTestCases();
  }, [problemId]);

  return (
    <>
      <Card className="border border-gray-200 shadow-sm mt-6">
        <CardHeader className="bg-[#1E293B] text-white">
          <div className="flex items-center justify-between">
            <CardTitle>Test Cases - {problemTitle}</CardTitle>
            <Button 
              size="sm"
              className="gap-2"
              onClick={() => setAddModalOpen(true)}
            >
              <Plus className="w-4 h-4" />
              Add Test Case
            </Button>
          </div>
        </CardHeader>
        <CardContent className="p-6">
          {isLoading ? (
            <p className="text-slate-600 py-8 text-center">Loading test cases...</p>
          ) : testCases.length === 0 ? (
            <p className="text-slate-600 py-8 text-center">
              No test cases yet. Click "Add Test Case" to create one.
            </p>
          ) : (
            <div className="space-y-4">
              {testCases.map((testCase, index) => (
                <div 
                  key={testCase.id} 
                  className="border border-gray-200 rounded-lg p-4 bg-gray-50"
                >
                  <div className="flex items-center justify-between mb-3">
                    <h4 className="text-slate-700">Test Case #{index + 1}</h4>
                    <Badge variant={testCase.isPublic ? 'default' : 'secondary'}>
                      {testCase.isPublic ? 'Public' : 'Private'}
                    </Badge>
                  </div>
                  
                  <div className="space-y-3">
                    <div>
                      <p className="text-sm text-slate-600 mb-1">Input Data:</p>
                      <pre className="bg-white border border-gray-200 rounded p-3 text-sm overflow-x-auto">
                        {testCase.inputData}
                      </pre>
                    </div>
                    
                    <div>
                      <p className="text-sm text-slate-600 mb-1">Expected Output:</p>
                      <pre className="bg-white border border-gray-200 rounded p-3 text-sm overflow-x-auto">
                        {testCase.expectedOutput}
                      </pre>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <AddTestCaseModal 
        open={addModalOpen}
        onOpenChange={setAddModalOpen}
        problemId={problemId}
        onSuccess={loadTestCases}
      />
    </>
  );
}
