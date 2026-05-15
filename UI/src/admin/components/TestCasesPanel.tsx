import { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Button } from './ui/button';
import { Plus, Pencil, Eye, EyeOff, Trash2 } from 'lucide-react';
import { AddTestCaseModal } from './AddTestCaseModal';
import { EditTestCaseModal } from './EditTestCaseModal';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from './ui/alert-dialog';
import { deleteTestCase, getTestCasesForProblem } from '../services/api';
import { TestCaseResponse } from '../types/api';
import { toast } from 'sonner';
import { StatusBadge } from '../../components/StatusBadge';

interface TestCasesPanelProps {
  problemId: number;
  problemTitle: string;
}

export function TestCasesPanel({ problemId, problemTitle }: TestCasesPanelProps) {
  const [testCases, setTestCases] = useState<TestCaseResponse[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingTestCase, setEditingTestCase] = useState<TestCaseResponse | null>(null);
  const [testCaseToDelete, setTestCaseToDelete] = useState<TestCaseResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

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

  const handleDeleteTestCase = async () => {
    if (!testCaseToDelete) return;

    setIsDeleting(true);
    try {
      await deleteTestCase(testCaseToDelete.id);
      toast.success('Test case deleted successfully');
      setTestCaseToDelete(null);
      await loadTestCases();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to delete test case');
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <>
      <Card className="border border-gray-200 shadow-sm mt-6">
        <CardHeader className="border-b border-slate-200 bg-slate-50">
          <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div>
              <CardTitle className="text-lg text-slate-950">Test Cases</CardTitle>
              <p className="mt-1 text-sm text-slate-600">{problemTitle}</p>
            </div>
            <Button 
              size="sm"
              className="gap-2 bg-blue-700 hover:bg-blue-800"
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
              No test cases yet. Click "Add Test Case" to create a sample or hidden judge test.
            </p>
          ) : (
            <div className="grid gap-4 lg:grid-cols-2">
              {testCases.map((testCase, index) => (
                <div
                  key={testCase.id}
                  className="rounded-xl bg-surface-container-low p-4"
                >
                   <div className="flex items-center justify-between mb-3">
                     <div>
                       <h4 className="font-display text-base font-semibold tracking-tight text-on-surface">Test Case #{index + 1}</h4>
                       <div className="mt-1 flex items-center gap-2">
                         {testCase.isPublic ? (
                           <Eye className="h-4 w-4 text-primary" />
                         ) : (
                           <EyeOff className="h-4 w-4 text-on-surface-soft" />
                         )}
                         <StatusBadge
                           kind="testcase"
                           value={testCase.isPublic ? 'PUBLIC SAMPLE' : 'PRIVATE'}
                           label={testCase.isPublic ? 'Public sample' : 'Private hidden'}
                         />
                       </div>
                     </div>
                     <div className="flex items-center gap-2">
                       <Button
                         size="sm"
                         variant="ghost"
                         className="h-7 px-2"
                         onClick={() => {
                           setEditingTestCase(testCase);
                           setEditModalOpen(true);
                         }}
                       >
                         <Pencil className="w-3.5 h-3.5" />
                       </Button>
                       <Button
                         size="sm"
                         variant="destructive"
                         className="h-7 px-2"
                         onClick={() => setTestCaseToDelete(testCase)}
                       >
                         <Trash2 className="w-3.5 h-3.5" />
                       </Button>
                     </div>
                   </div>

                  <div className="space-y-3">
                    <div>
                      <p className="mb-1.5 text-[10px] font-medium uppercase tracking-[0.18em] text-on-surface-soft">Input Data</p>
                      <pre className="rounded-lg bg-surface-container-lowest p-3 text-xs overflow-x-auto font-mono text-on-surface dark:bg-black">
                        {testCase.inputData}
                      </pre>
                    </div>

                    <div>
                      <p className="mb-1.5 text-[10px] font-medium uppercase tracking-[0.18em] text-on-surface-soft">Expected Output</p>
                      <pre className="rounded-lg bg-surface-container-lowest p-3 text-xs overflow-x-auto font-mono text-tertiary dark:bg-black">
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

      {editingTestCase && (
        <EditTestCaseModal
          open={editModalOpen}
          onOpenChange={setEditModalOpen}
          testCase={editingTestCase}
          onSuccess={loadTestCases}
        />
      )}

      <AlertDialog
        open={Boolean(testCaseToDelete)}
        onOpenChange={(open) => {
          if (!open && !isDeleting) setTestCaseToDelete(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete test case?</AlertDialogTitle>
            <AlertDialogDescription>
              This permanently removes the selected test case from "{problemTitle}". This cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isDeleting}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-rose-600 text-white hover:bg-rose-700"
              disabled={isDeleting}
              onClick={(event) => {
                event.preventDefault();
                handleDeleteTestCase();
              }}
            >
              {isDeleting ? 'Deleting...' : 'Delete Test Case'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
