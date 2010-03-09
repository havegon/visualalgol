package visualalgol.casosdeuso;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import javax.swing.JFileChooser;

import visualalgol.entidades.Algoritmo;
import visualalgol.swing.MainFrame;

public class AbrirAlgoritmo extends SalvarAlgoritmo {
	private static File algoritmoAberto;

	@Override
	public void executar(MainFrame mainFrame) {
		int returnVal = fc.showOpenDialog(mainFrame);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File file = fc.getSelectedFile();
			abrirArquivo(file, mainFrame);
			
		}
	}

	private void abrirArquivo(File file, MainFrame mainFrame) {
		FileInputStream fis = null;
		ObjectInputStream in = null;
		try {
			fis = new FileInputStream(file);
			in = new ObjectInputStream(fis);
			Algoritmo algoritmo = (Algoritmo) in.readObject();
			mainFrame.setAlgoritmo(algoritmo);
			in.close();
			
			//lembrar quem foi o ultimo aberto
			algoritmoAberto = file;
			
			mainFrame.setTitle(file.getPath());
			
			// Colocar na lista de recentes
			List<String> lista = mainFrame.getMenuPrincipal().getArquivoRecente().getPaths();
			if (!lista.contains(file.getAbsolutePath())) {
				lista.add(0, file.getAbsolutePath());
				// retirar o excesso
				for (int i = 10; i < lista.size(); i++) {
					lista.remove(i);
				}
				salvarRecentes(mainFrame);
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (ClassNotFoundException ex) {
			ex.printStackTrace();
		}
	}

	private void salvarRecentes(MainFrame mainFrame) {
		File file = new File(getPastaDoPrograma(), "recentes.txt");
		FileOutputStream fos = null;
		ObjectOutputStream out = null;
		try {
			fos = new FileOutputStream(file);
			out = new ObjectOutputStream(fos);
			out.writeObject(mainFrame.getMenuPrincipal().getArquivoRecente());
			out.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public void abrirArquivo(String path, MainFrame mainFrame) {
		abrirArquivo(new File(path), mainFrame);
	}

	public static File getAlgoritmoAberto() {
		return algoritmoAberto;
	}
	
}
